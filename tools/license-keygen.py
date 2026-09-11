#!/usr/bin/env python3
"""DeryCode School Report Maker - licence key generator.
Usage: python3 license-keygen.py SD 2027-09-09
Plans: ST Starter(100) | SD Standard(300) | GR Growth(800) | SC School(2000) | MB Multi-Branch"""
import sys

SECRET = "DeryCode-SRS-2026"

def h32(s: str) -> str:
    h = 5381
    for c in s:
        h = ((h * 33) + ord(c)) & 0xFFFFFFFF
    return "%08x" % h

def main():
    plan, expiry = sys.argv[1].upper(), sys.argv[2].replace("-", "")
    print(f"{plan}-{expiry}-{h32(f'{SECRET}|{plan}|{expiry}').upper()}")

if __name__ == "__main__":
    main()
