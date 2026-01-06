
import struct
import sys

TARGET_VALUES = [
    # Steps
    42, 13, 884, 1068, 
    # Kcal
    1763, 990, 1704, 1925, 1540, 1797, 1776
]

def search_values(filepath):
    print(f"Searching for values: {TARGET_VALUES}")
    
    with open(filepath, 'rb') as f:
        # Read entire file to memory for simple parsing (it's small)
        data = f.read()

    # Find all 0x0013 payloads (Simplified parser for speed/robustness or reuse previous logic)
    # Re-using the reliable logic from parse_btsnoop.py is safer.
    
    packet_count = 0
    offset = 16 # Skip btsnoop header
    
    while offset < len(data):
        # Record Header (24 bytes)
        if offset + 24 > len(data): break
        
        orig_len, inc_len, flags, drops, ts = struct.unpack('>IIIIQ', data[offset:offset+24])
        offset += 24
        
        packet_data = data[offset:offset+inc_len]
        offset += inc_len
        
        if len(packet_data) < 1: continue
        
        # ACL Data (0x02) -> Handle (2) -> L2CAP (4) -> ATT (0x0004)
        if packet_data[0] == 0x02:
             if len(packet_data) >= 9: # Min size for ATT payload
                 l2cap_cid = struct.unpack('<H', packet_data[7:9])[0]
                 if l2cap_cid == 0x0004:
                     payload = packet_data[9:]
                     opcode = payload[0]
                     
                     # 0x1B Notification or 0x0A Read Response often carry data
                     if opcode == 0x1B: 
                         att_val = payload[3:]
                         # Decode XOR 0xFF
                         decoded = bytearray(b ^ 0xFF for b in att_val)
                         
                         check_payload(decoded, packet_count)
                         
        packet_count += 1

def check_payload(decoded, packet_num):
    # Only check payloads that look like they might be the "ec" response or data stream
    # They usually start with 0xFA (header) or are part of the stream
    
    # Brute force search for values
    hex_str = decoded.hex().upper()
    
    found = False
    for val in TARGET_VALUES:
        # SEARCH UINT16 LE
        val_bytes = struct.pack('<H', val)
        if val_bytes in decoded:
            idx = decoded.find(val_bytes)
            print(f"[Packet #{packet_num}] FOUND {val} (0x{val:04X}) at index {idx}")
            print(f"   Context: {decoded[max(0, idx-4):idx+6].hex().upper()}")
            found = True
            
        # SEARCH UINT32 LE
        val_bytes32 = struct.pack('<I', val)
        if val_bytes32 in decoded:
             idx = decoded.find(val_bytes32)
             print(f"[Packet #{packet_num}] FOUND {val} (u32) at index {idx}")
             found = True

    if found and len(decoded) > 10:
         print(f" -- Full Packet Hex: {hex_str}")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python3 search_values.py <file>")
    else:
        search_values(sys.argv[1])
