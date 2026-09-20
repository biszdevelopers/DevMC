#!/usr/bin/env python3
"""Reads the seed from a world's world_gen_settings.dat (NBT, possibly gzipped).

Usage: python nbt_seed.py <path-to-world_gen_settings.dat>
"""
import gzip
import struct
import sys

TAG_END = 0
TAG_COMPOUND = 10
TAG_LONG = 4


def read_utf(stream):
    length = struct.unpack(">H", stream.read(2))[0]
    return stream.read(length).decode("utf-8")


def read_payload(stream, tag):
    if tag == 1:  # byte
        return struct.unpack(">b", stream.read(1))[0]
    if tag == 2:  # short
        return struct.unpack(">h", stream.read(2))[0]
    if tag == 3:  # int
        return struct.unpack(">i", stream.read(4))[0]
    if tag == 4:  # long
        return struct.unpack(">q", stream.read(8))[0]
    if tag == 5:  # float
        return struct.unpack(">f", stream.read(4))[0]
    if tag == 6:  # double
        return struct.unpack(">d", stream.read(8))[0]
    if tag == 7:  # byte array
        length = struct.unpack(">i", stream.read(4))[0]
        return stream.read(length)
    if tag == 8:  # string
        return read_utf(stream)
    if tag == 9:  # list
        element_type = stream.read(1)[0]
        length = struct.unpack(">i", stream.read(4))[0]
        return [read_payload(stream, element_type) for _ in range(length)]
    if tag == 10:  # compound
        return read_compound(stream)
    if tag == 11:  # int array
        length = struct.unpack(">i", stream.read(4))[0]
        return [struct.unpack(">i", stream.read(4))[0] for _ in range(length)]
    if tag == 12:  # long array
        length = struct.unpack(">i", stream.read(4))[0]
        return [struct.unpack(">q", stream.read(8))[0] for _ in range(length)]
    return None


def read_compound(stream):
    result = {}
    while True:
        tag = stream.read(1)
        if not tag or tag[0] == TAG_END:
            break
        name = read_utf(stream)
        result[name] = read_payload(stream, tag[0])
    return result


def find_seeds(node, path="", out=None):
    if out is None:
        out = []
    if isinstance(node, dict):
        for key, value in node.items():
            if key == "seed" and isinstance(value, int):
                out.append((path + "/" + key, value))
            find_seeds(value, path + "/" + key, out)
    elif isinstance(node, list):
        for index, value in enumerate(node):
            find_seeds(value, "%s[%d]" % (path, index), out)
    return out


def main():
    path = sys.argv[1]
    with open(path, "rb") as f:
        head = f.read(2)
        f.seek(0)
        data = f.read()
    if head == b"\x1f\x8b":
        data = gzip.decompress(data)
    if data[:1] != b"\x0a":
        print("not an NBT compound file")
        return
    import io

    stream = io.BytesIO(data)
    tag = stream.read(1)[0]
    if tag != TAG_COMPOUND:
        print("root is not a compound")
        return
    read_utf(stream)  # root name
    root = read_compound(stream)
    for path, seed in find_seeds(root):
        print("%s = %d" % (path, seed))


if __name__ == "__main__":
    main()
