#!/usr/bin/env python3
"""Check country-aware Amazon store links: data-asin + fallback + domain map."""
import re
from pathlib import Path

BASE = Path(__file__).parent
FILES = ["index.html", "privacy.html", "terms.html", "privacy-policy.html", "terms-conditions.html"]

# expected domain map (must match inline JS)
EXPECTED = {
    "US":"amazon.com","GB":"amazon.co.uk","UK":"amazon.co.uk","DE":"amazon.de","FR":"amazon.fr",
    "IT":"amazon.it","ES":"amazon.es","NL":"amazon.nl","SE":"amazon.se",
    "PL":"amazon.pl","CA":"amazon.ca","MX":"amazon.com.mx","BR":"amazon.com.br",
    "IN":"amazon.in","JP":"amazon.co.jp","AU":"amazon.com.au","SG":"amazon.sg",
    "AE":"amazon.ae","SA":"amazon.sa","TR":"amazon.com.tr","EG":"amazon.eg","BE":"amazon.com.be","IE":"amazon.ie",
}

# (a) every data-asin link has valid amazon.com fallback containing same ASIN
asin_re = re.compile(r'<a\b[^>]*\bdata-asin="(B0[A-Z0-9]+)"[^>]*>', re.I)
href_re = re.compile(r'href="([^"]*)"')

for fname in FILES:
    html = (BASE/fname).read_text()
    for m in asin_re.finditer(html):
        asin = m.group(1)
        tag = m.group(0)
        hm = href_re.search(tag)
        assert hm, f"{fname}: data-asin={asin} missing href"
        href = hm.group(1)
        assert "amazon.com/dp/"+asin in href, f"{fname}: data-asin={asin} href fallback broken: {href}"
        assert 'data-asin="'+asin+'"' in tag, f"{fname}: data-asin mismatch"

# must find at least one link per required ASIN across site (apps + both live books)
all_asins = {m.group(1) for f in FILES for m in asin_re.finditer((BASE/f).read_text())}
# also scan books.html for book ASINs
try:
    all_asins |= {m.group(1) for m in asin_re.finditer((BASE/"books.html").read_text())}
except FileNotFoundError:
    pass
assert "B0H85JCRT1" in all_asins, "B0H85JCRT1 not found in any data-asin"
assert "B0FKZMV45T" in all_asins, "B0FKZMV45T not found in any data-asin"
assert "B0HKMWWLCH" in all_asins, "B0HKMWWLCH not found in any data-asin"
assert "B0HJRHS916" in all_asins, "B0HJRHS916 not found in any data-asin"

# (b) country->domain map: extract from index.html inline JS
js = (BASE/"index.html").read_text()
# extract map literal: var m={...} or var domains={...}
mm = re.search(r'var (?:m|domains)=\{([^}]+)\}', js)
assert mm, "country->domain map not found in index.html"
pairs = re.findall(r'"([A-Z]{2})"\s*:\s*"([^"]+)"', mm.group(0))
assert pairs, "no pairs in map"
codes = [c for c,_ in pairs]
domains = [d for _,d in pairs]
assert len(codes)==len(set(codes)), f"duplicate country codes: {codes}"
# domains may legally repeat for GB/UK alias; allow but verify US present
assert "amazon.com" in domains, "amazon.com missing from domains"
assert all(c.strip() for c in codes), "empty country code"
assert all(d.strip() for d in domains), "empty domain"
# compare against expected set
found = dict(pairs)
assert found == EXPECTED, f"map mismatch: got {found}"
# guard rails present
assert "try{" in js and "catch" in js, "missing try/catch guard"
assert 'querySelectorAll("a[data-asin]")' in js or "querySelectorAll('a[data-asin]')" in js or 'querySelectorAll("a[data-asin]")' in js, "missing data-asin selector"

print("ALL STORE LINK CHECKS PASS")
