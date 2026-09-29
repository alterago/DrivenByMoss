#!/usr/bin/env python3
"""Make DrivenByMoss's REAPER restart action refresh MIDI metadata first."""

from __future__ import annotations

import struct
import sys
from pathlib import Path


def u2(data: bytes, offset: int) -> int:
    return struct.unpack_from(">H", data, offset)[0]


def u4(data: bytes, offset: int) -> int:
    return struct.unpack_from(">I", data, offset)[0]


def constant_pool(data: bytes) -> tuple[dict[int, tuple], int]:
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("Not a Java class file")

    count = u2(data, 8)
    entries: dict[int, tuple] = {}
    offset = 10
    index = 1
    while index < count:
        tag = data[offset]
        offset += 1
        if tag == 1:
            size = u2(data, offset)
            offset += 2
            entries[index] = (tag, data[offset : offset + size].decode("utf-8"))
            offset += size
        elif tag in (3, 4):
            entries[index] = (tag,)
            offset += 4
        elif tag in (5, 6):
            entries[index] = (tag,)
            offset += 8
            index += 1
        elif tag in (7, 8, 16, 19, 20):
            entries[index] = (tag, u2(data, offset))
            offset += 2
        elif tag in (9, 10, 11, 12, 17, 18):
            entries[index] = (tag, u2(data, offset), u2(data, offset + 2))
            offset += 4
        elif tag == 15:
            entries[index] = (tag, data[offset], u2(data, offset + 1))
            offset += 3
        else:
            raise ValueError(f"Unsupported constant-pool tag {tag} at index {index}")
        index += 1
    return entries, offset


def utf8(entries: dict[int, tuple], index: int) -> str:
    entry = entries[index]
    if entry[0] != 1:
        raise ValueError(f"Constant {index} is not UTF-8")
    return entry[1]


def class_name(entries: dict[int, tuple], index: int) -> str:
    entry = entries[index]
    if entry[0] != 7:
        raise ValueError(f"Constant {index} is not a class")
    return utf8(entries, entry[1])


def member_ref(
    entries: dict[int, tuple], tag: int, owner: str, name: str, descriptor: str
) -> int:
    for index, entry in entries.items():
        if entry[0] != tag:
            continue
        target_owner = class_name(entries, entry[1])
        name_and_type = entries[entry[2]]
        target_name = utf8(entries, name_and_type[1])
        target_descriptor = utf8(entries, name_and_type[2])
        if (target_owner, target_name, target_descriptor) == (owner, name, descriptor):
            return index
    raise ValueError(f"Missing member reference: {owner}.{name}{descriptor}")


def skip_attributes(data: bytes, offset: int, count: int) -> int:
    for _ in range(count):
        size = u4(data, offset + 2)
        offset += 6 + size
    return offset


def skip_members(data: bytes, offset: int, count: int) -> int:
    for _ in range(count):
        attribute_count = u2(data, offset + 6)
        offset = skip_attributes(data, offset + 8, attribute_count)
    return offset


def patch(data: bytes) -> tuple[bytes, bool]:
    entries, offset = constant_pool(data)
    instance_manager_field = member_ref(
        entries,
        9,
        "de/mossgrabers/reaper/MainApp",
        "instanceManager",
        "Lde/mossgrabers/reaper/controller/ControllerInstanceManager;",
    )
    refresh_method = member_ref(
        entries,
        10,
        "de/mossgrabers/reaper/controller/ControllerInstanceManager",
        "refreshMIDIAll",
        "()V",
    )
    injected = b"\x2a\xb4" + struct.pack(">H", instance_manager_field)
    injected += b"\xb6" + struct.pack(">H", refresh_method)

    interface_count = u2(data, offset + 6)
    offset += 8 + 2 * interface_count
    field_count = u2(data, offset)
    offset = skip_members(data, offset + 2, field_count)
    method_count = u2(data, offset)
    offset += 2

    for _ in range(method_count):
        name = utf8(entries, u2(data, offset + 2))
        descriptor = utf8(entries, u2(data, offset + 4))
        attribute_count = u2(data, offset + 6)
        attribute_offset = offset + 8

        for _ in range(attribute_count):
            attribute_name_index = u2(data, attribute_offset)
            attribute_name = utf8(entries, attribute_name_index)
            attribute_size = u4(data, attribute_offset + 2)
            attribute_end = attribute_offset + 6 + attribute_size

            if name == "restartControllers" and descriptor == "()V" and attribute_name == "Code":
                info = attribute_offset + 6
                max_stack = u2(data, info)
                max_locals = u2(data, info + 2)
                code_size = u4(data, info + 4)
                code_start = info + 8
                code = data[code_start : code_start + code_size]
                if code.startswith(injected):
                    return data, False

                exception_offset = code_start + code_size
                exception_count = u2(data, exception_offset)
                if exception_count:
                    raise ValueError("restartControllers unexpectedly has an exception table")

                new_code = injected + code
                new_info = struct.pack(">HHI", max(max_stack, 1), max_locals, len(new_code))
                new_info += new_code + struct.pack(">HH", 0, 0)
                new_attribute = struct.pack(">HI", attribute_name_index, len(new_info)) + new_info
                return data[:attribute_offset] + new_attribute + data[attribute_end:], True

            attribute_offset = attribute_end

        offset = attribute_offset

    raise ValueError("MainApp.restartControllers() was not found")


def main() -> int:
    if len(sys.argv) != 3:
        print(f"usage: {Path(sys.argv[0]).name} INPUT.class OUTPUT.class", file=sys.stderr)
        return 2
    source = Path(sys.argv[1])
    target = Path(sys.argv[2])
    patched, changed = patch(source.read_bytes())
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(patched)
    print("patched" if changed else "already patched")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
