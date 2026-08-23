package dev.kof.cli;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * JdwpClient — minimal raw JDWP wire client (no jdk.jdi dependency).
 *
 * Drives a debuggee JVM launched with -agentlib:jdwp. Used by KofDebug
 * to set breakpoints by Kof source line (the JVM backend emits the
 * LineNumberTable) and to read stack frames.
 */
final class JdwpClient {

    record FrameInfo(long methodId, String methodName, int line, long codeIndex) {
    }

    private final String host;
    private final int port;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private int idSeq = 1;
    private int refSize = 8;

    JdwpClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    void connect() throws IOException {
        socket = new Socket(host, port);
        socket.setSoTimeout(20000);
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
        out.write("JDWP-Handshake".getBytes(StandardCharsets.US_ASCII));
        out.flush();
        byte[] reply = new byte[14];
        in.readFully(reply);
        if (!"JDWP-Handshake".equals(new String(reply, StandardCharsets.US_ASCII))) {
            throw new IOException("JDWP handshake failed");
        }
        Packet ids = sendCommand(1, 7, new Packet()); // VM.IDSizes
        ids.readInt(); // fieldIDSize
        ids.readInt(); // methodIDSize
        ids.readInt(); // objectIDSize
        refSize = ids.readInt(); // referenceTypeIDSize
        ids.readInt(); // frameIDSize
    }

    /** VM.Resume */
    void resume() throws IOException {
        sendCommand(1, 9, new Packet());
    }

    /** VM.Dispose */
    void dispose() throws IOException {
        try {
            sendCommand(1, 6, new Packet());
        } catch (IOException ignored) {
        }
    }

    /**
     * EventRequest.Set (15,1) for ClassPrepare of the given class.
     * Returns the request id. Starts the event loop.
     */
    long setClassPrepareRequest(String className, EventHandler handler) throws IOException {
        Packet req = new Packet();
        req.writeByte(4);   // event kind: ClassPrepare
        req.writeByte(0);   // suspend policy: NONE
        req.writeInt(1);    // modifier count
        req.writeByte(1);   // ClassMatch
        req.writeString(className);
        Packet reply = sendCommand(15, 1, req);
        long requestId = reply.readInt();
        Thread loop = new Thread(() -> eventLoop(handler), "jdwp-events");
        loop.setDaemon(true);
        loop.start();
        return requestId;
    }

    /**
     * EventRequest.Set (15,1) for a line breakpoint in the given class.
     * The class must be prepared; line maps through the Kof LineNumberTable.
     */
    void setLineBreakpoint(String className, int line) throws IOException {
        setLineBreakpoint(typeIdOfClass(className), line);
    }

    void setLineBreakpoint(long typeId, int line) throws IOException {
        long methodId = methodWithLine(typeId, line);
        long[] lines = lineTable(typeId, methodId);
        long codeIndex = lines[0];
        Packet req = new Packet();
        req.writeByte(2);   // event kind: Breakpoint
        req.writeByte(0);   // suspend policy: NONE
        req.writeInt(1);    // modifier count
        req.writeByte(3);   // LocationOnly
        req.writeReference(typeId);
        req.writeReference(methodId);
        req.writeLong(codeIndex);
        sendCommand(15, 1, req).skipRemaining();
    }

    /** ReferenceType.Methods (2,5): map method names to ids. */
    private long methodWithLine(long typeId, int line) throws IOException {
        Packet req = new Packet();
        req.writeReference(typeId);
        Packet reply = sendCommand(2, 5, req);
        int count = reply.readInt();
        long bestMethod = 0;
        for (int i = 0; i < count; i++) {
            long methodId = reply.readReference();
            reply.readString();
            reply.readInt(); // modifiers
        }
        // find a method whose line table contains the requested line
        bestMethod = findMethodWithLine(typeId, line);
        if (bestMethod == 0) {
            throw new IOException("no method contains line " + line);
        }
        return bestMethod;
    }

    private long findMethodWithLine(long typeId, int line) throws IOException {
        Packet req = new Packet();
        req.writeReference(typeId);
        Packet reply = sendCommand(2, 5, req);
        int count = reply.readInt();
        List<long[]> methods = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long methodId = reply.readReference();
            String name = reply.readString();
            reply.readInt();
            methods.add(new long[]{methodId});
            if ("<init>".equals(name) || "<clinit>".equals(name)) continue;
            try {
                long[] lines = lineTable(typeId, methodId);
                for (int li = 0; li + 1 < lines.length; li += 2) {
                    if (lines[li] == line) {
                        return methodId;
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return 0;
    }

    /** Method.LineTable (6,1): returns flattened [line, codeIndex, ...]. */
    private long[] lineTable(long typeId, long methodId) throws IOException {
        Packet req = new Packet();
        req.writeReference(typeId);
        req.writeReference(methodId);
        Packet reply = sendCommand(6, 1, req);
        reply.readLong(); // start
        reply.readLong(); // end
        int count = reply.readInt();
        long[] lines = new long[count * 2];
        for (int i = 0; i < count; i++) {
            lines[i * 2] = reply.readLong();     // line code (long in JDWP)
            lines[i * 2 + 1] = reply.readLong(); // code index
        }
        return lines;
    }

    private long typeIdOfClass(String className) throws IOException {
        Packet req = new Packet();
        req.writeString(className);
        Packet reply = sendCommand(1, 2, req); // VM.ClassesBySignature
        int count = reply.readInt();
        for (int i = 0; i < count; i++) {
            reply.readByte(); // refTypeTag
            long typeId = reply.readReference();
            String signature = reply.readString();
            if (("L" + className + ";").equals(signature)) {
                return typeId;
            }
        }
        throw new IOException("class not prepared: " + className);
    }

    /** VM.AllThreads (1,4). */
    List<Long> allThreads() throws IOException {
        Packet reply = sendCommand(1, 4, new Packet());
        int count = reply.readInt();
        List<Long> threads = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            threads.add(reply.readReference());
        }
        return threads;
    }

    /** ThreadReference.Frames (10,6): stack frames of a thread. */
    List<FrameInfo> frames(long threadId, int depth) throws IOException {
        Packet req = new Packet();
        req.writeReference(threadId);
        req.writeInt(0);   // startFrame
        req.writeInt(depth > 0 ? depth : 100);
        Packet reply = sendCommand(10, 6, req);
        int count = reply.readInt();
        List<FrameInfo> frames = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            reply.readReference(); // frameID
            long locationType = reply.readByte();
            if (locationType == 1) { // ObjectReference
                reply.readReference();
                reply.readReference();
                reply.readLong();
            } else { // ClassType location (for methods)
                long typeId = reply.readReference();
                long methodId = reply.readReference();
                long codeIndex = reply.readLong();
                String methodName = methodName(typeId, methodId);
                int line = lineAt(typeId, methodId, codeIndex);
                frames.add(new FrameInfo(methodId, methodName, line, codeIndex));
            }
        }
        return frames;
    }

    private String methodName(long typeId, long methodId) throws IOException {
        Packet req = new Packet();
        req.writeReference(typeId);
        req.writeReference(methodId);
        Packet reply = sendCommand(6, 2, req); // Method.VariableTable
        reply.skipRemaining();
        Packet req2 = new Packet();
        req2.writeReference(typeId);
        Packet methods = sendCommand(2, 5, req2);
        int count = methods.readInt();
        String name = "?";
        for (int i = 0; i < count; i++) {
            long id = methods.readReference();
            String n = methods.readString();
            methods.readInt();
            if (id == methodId) {
                name = n;
                break;
            }
        }
        return name;
    }

    private int lineAt(long typeId, long methodId, long codeIndex) throws IOException {
        long[] lines = lineTable(typeId, methodId);
        int best = -1;
        for (int i = 0; i + 1 < lines.length; i += 2) {
            if (lines[i + 1] <= codeIndex) {
                best = (int) lines[i];
            }
        }
        return best;
    }

    private void eventLoop(EventHandler handler) {
        try {
            while (true) {
                Packet evt = readPacket();
                int kind = evt.readByte();
                evt.readInt(); // requestId
                long threadId = evt.readReference();
                long typeId = 0;
                if (kind == 4) { // ClassPrepare: tag, typeID, signature, status
                    evt.readByte();
                    typeId = evt.readReference();
                } else if (kind == 2) { // Breakpoint: location (tag + type/method/codeIndex)
                    int tag = evt.readByte();
                    if (tag == 1) {
                        evt.readReference();
                    }
                    typeId = evt.readReference();
                }
                handler.onEvent(kind, threadId, typeId);
            }
        } catch (IOException e) {
            handler.onDisconnect();
        }
    }

    interface EventHandler {
        void onEvent(int kind, long threadId, long typeId);

        default void onDisconnect() {
        }
    }

    private Packet sendCommand(int cmdSet, int cmd, Packet data) throws IOException {
        byte[] payload = data.toByteArray();
        int length = 11 + payload.length;
        out.writeInt(length);
        out.writeInt(idSeq);
        out.writeByte(0); // flags: none
        out.writeByte(cmdSet);
        out.writeByte(cmd);
        out.write(payload);
        out.flush();
        int myId = idSeq++;
        while (true) {
            Packet reply = readPacket();
            if (reply.id == myId) {
                if (reply.errorCode != 0) {
                    throw new IOException("JDWP error " + reply.errorCode);
                }
                return reply;
            }
        }
    }

    private Packet readPacket() throws IOException {
        int length = in.readInt();
        int id = in.readInt();
        int flags = in.readByte();
        if (flags == 0x80) { // reply
            int error = in.readShort();
            byte[] payload = new byte[length - 11];
            in.readFully(payload);
            return new Packet(id, error, payload);
        }
        in.readByte(); // cmdSet
        in.readByte(); // cmd
        byte[] payload = new byte[length - 11];
        in.readFully(payload);
        return new Packet(id, 0, payload);
    }

    private static final class Packet {
        int id;
        int errorCode;
        private final List<Byte> bytes = new ArrayList<>();
        private byte[] data;
        private int pos;

        Packet() {
        }

        Packet(int id, int errorCode, byte[] data) {
            this.id = id;
            this.errorCode = errorCode;
            this.data = data;
            this.pos = 0;
        }

        byte[] toByteArray() {
            byte[] arr = new byte[bytes.size()];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = bytes.get(i);
            }
            return arr;
        }

        void writeByte(int b) {
            bytes.add((byte) b);
        }

        void writeInt(int v) {
            writeByte(v >>> 24);
            writeByte(v >>> 16);
            writeByte(v >>> 8);
            writeByte(v);
        }

        void writeLong(long v) {
            writeInt((int) (v >>> 32));
            writeInt((int) v);
        }

        void writeString(String s) {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            writeInt(b.length);
            for (byte x : b) {
                bytes.add(x);
            }
        }

        void writeReference(long ref) {
            writeLong(ref);
        }

        int readByte() {
            return data[pos++] & 0xFF;
        }

        int readShort() {
            return (readByte() << 8) | readByte();
        }

        int readInt() {
            return (readByte() << 24) | (readByte() << 16) | (readByte() << 8) | readByte();
        }

        long readLong() {
            return ((long) readInt() << 32) | (readInt() & 0xFFFFFFFFL);
        }

        long readReference() {
            return readLong();
        }

        String readString() {
            int len = readInt();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < len; i++) {
                sb.append((char) readByte());
            }
            return sb.toString();
        }

        void skipRemaining() {
            pos = data.length;
        }
    }
}