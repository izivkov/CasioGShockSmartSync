
import struct
import sys

# Raw hex strings from the previous analysis (Packet 325, response to 'ec')
# We skip the first byte '05' which seems to be a sequence/type header
payloads = [
    "9A9CFFFFFFA40100000001010001009FFFFFFFFF9DFFFFFFFFFFF8FFFFEAFFFFFFFFFFF7F3F8D7FFFFDB9E010101010101010101010101010101010101010101010101010101010101010101010101010101010101010101010101FFA192FFC5FFC8FF4784",
    "AFDAFFFFFFFFFF13F7FFFF13F7FFFF13F7FFFF13F7FFFF13F7FFFF13F7FFFF13F707B7", # P% response
    "ADAAFFFFFFACE613F7C1B413F7BAE613F79EE613F7A0FF13F7AAE613F7BDE613F79EFF13F7B6FF13F7AAFF13F7B0E613F7C4B413F7BCCD13F7BBE613F7BDE613F7B9E613F7C2B413F7ADFF13F7B0FF13F7BFE613F7001F" # RU response
]

def xor_decode(hex_str, key=0xFF):
    data = bytes.fromhex(hex_str)
    decoded = bytearray()
    for b in data:
        decoded.append(b ^ key)
    return decoded

print("Decoding Payloads (XOR 0xFF):")
print("-" * 30)

for i, p in enumerate(payloads):
    decoded = xor_decode(p)
    print(f"\nPayload {i+1}:")
    print(f"Hex: {decoded.hex().upper()}")
    
    # Try to print as ASCII where possible to spot text tags
    ascii_repr = ""
    for b in decoded:
        if 0x20 <= b <= 0x7E:
            ascii_repr += chr(b)
        else:
            ascii_repr += "."
    print(f"ASCII: {ascii_repr}")

