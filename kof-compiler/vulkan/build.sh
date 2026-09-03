#!/usr/bin/env bash
# build.sh — compila e instala a libvkchain.so (M32.3)
# Requer: gcc, libvulkan-dev (headers vulkan/vulkan.h)
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
gcc -O1 -shared -fPIC -o "$DIR/libvkchain.so" "$DIR/vkchain.c" -lvulkan
gcc -O1 -shared -fPIC -o "$DIR/libvkchain64.so" "$DIR/vkchain64.c" -lvulkan
if [ -w /usr/local/lib ] || sudo -n true 2>/dev/null; then
    sudo cp "$DIR/libvkchain.so" "$DIR/libvkchain64.so" /usr/local/lib/ && sudo ldconfig
    echo "instalada em /usr/local/lib/libvkchain.so"
else
    echo "libvkchain.so gerada em $DIR — copie para /usr/local/lib e rode ldconfig"
    echo "(ou deixe ./libvkchain.so ao lado do binário nativo; KOF_GPU_SPV aponta o .spv)"
fi
