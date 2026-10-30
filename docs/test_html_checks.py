#!/usr/bin/env python3
"""HTML validation checks for static site (index.html, privacy.html, terms.html, privacy-policy.html, terms-conditions.html)"""
import os
import sys
from pathlib import Path
from html.parser import HTMLParser
from urllib.parse import urlparse

# Setup
BASE_DIR = Path(__file__).parent
FILES_TO_TEST = ["index.html", "privacy.html", "terms.html", "privacy-policy.html", "terms-conditions.html"]
AMAZON_REVIEW_DIR = BASE_DIR / "amazon-review-screenshots"

results = {"pass": [], "fail": []}
fail_count = 0

def report(check_name, file_name, status, details=""):
    global fail_count
    msg = f"  {file_name:15} {check_name:40} {status:6}"
    if details:
        msg += f" | {details}"
    print(msg)
    if status == "FAIL":
        fail_count += 1
        results["fail"].append((file_name, check_name, details))
    else:
        results["pass"].append((file_name, check_name))

# ============ CHECK 1: HTML Parse/Tag Balance ============
print("\n1. HTML PARSE/TAG-BALANCE CHECK")
print("-" * 100)

for fname in FILES_TO_TEST:
    fpath = BASE_DIR / fname
    try:
        class BalanceParser(HTMLParser):
            def __init__(self):
                super().__init__()
                self.tag_stack = []
                self.errors = []
                self.void_tags = {'area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input', 'link', 'meta', 'param', 'source', 'track', 'wbr'}
            
            def handle_starttag(self, tag, attrs):
                if tag.lower() not in self.void_tags:
                    self.tag_stack.append(tag.lower())
            
            def handle_endtag(self, tag):
                tag = tag.lower()
                if tag in self.void_tags:
                    return
                if not self.tag_stack:
                    self.errors.append(f"Closing tag without opening: </{tag}>")
                elif self.tag_stack[-1] == tag:
                    self.tag_stack.pop()
                else:
                    self.errors.append(f"Mismatched tag: expected </{self.tag_stack[-1] if self.tag_stack else '?'}>, got </{tag}>")
        
        parser = BalanceParser()
        with open(fpath) as f:
            parser.feed(f.read())
        
        if parser.tag_stack:
            parser.errors.append(f"Unclosed tags: {', '.join(f'<{t}>' for t in parser.tag_stack)}")
        
        if parser.errors:
            report("HTML Parse/Tag Balance", fname, "FAIL", "; ".join(parser.errors[:2]))
        else:
            report("HTML Parse/Tag Balance", fname, "PASS")
    except Exception as e:
        report("HTML Parse/Tag Balance", fname, "FAIL", str(e))

# ============ CHECK 2: Duplicate IDs ============
print("\n2. DUPLICATE IDS CHECK")
print("-" * 100)

for fname in FILES_TO_TEST:
    fpath = BASE_DIR / fname
    try:
        class IDParser(HTMLParser):
            def __init__(self):
                super().__init__()
                self.ids = []
            
            def handle_starttag(self, tag, attrs):
                for key, val in attrs:
                    if key == 'id' and val:
                        self.ids.append(val)
        
        parser = IDParser()
        with open(fpath) as f:
            parser.feed(f.read())
        
        duplicates = set(id for id in parser.ids if parser.ids.count(id) > 1)
        
        if duplicates:
            report("Duplicate IDs", fname, "FAIL", f"IDs: {', '.join(sorted(duplicates))}")
        else:
            report("Duplicate IDs", fname, "PASS")
    except Exception as e:
        report("Duplicate IDs", fname, "FAIL", str(e))

# ============ CHECK 3: Link Integrity (relative files) ============
print("\n3. LINK INTEGRITY (relative files must exist)")
print("-" * 100)

for fname in FILES_TO_TEST:
    fpath = BASE_DIR / fname
    try:
        class LinkParser(HTMLParser):
            def __init__(self):
                super().__init__()
                self.bad_links = []
            
            def handle_starttag(self, tag, attrs):
                if tag == 'a':
                    for key, val in attrs:
                        if key == 'href' and val:
                            # Check if relative file link (not http/https, not #anchor-only)
                            if not val.startswith(('http://', 'https://', '#', 'mailto:')):
                                # Extract file path (before # if present)
                                fpath_part = val.split('#')[0]
                                if fpath_part:  # Non-empty file part
                                    target_path = BASE_DIR / fpath_part
                                    if not target_path.exists():
                                        self.bad_links.append(f"{val}")
        
        parser = LinkParser()
        with open(fpath) as f:
            parser.feed(f.read())
        
        if parser.bad_links:
            report("Link Integrity", fname, "FAIL", f"Missing: {', '.join(parser.bad_links)}")
        else:
            report("Link Integrity", fname, "PASS")
    except Exception as e:
        report("Link Integrity", fname, "FAIL", str(e))

# ============ CHECK 4: Anchor Integrity ============
print("\n4. ANCHOR INTEGRITY (href=#x must have id=x in same file)")
print("-" * 100)

for fname in FILES_TO_TEST:
    fpath = BASE_DIR / fname
    try:
        class AnchorParser(HTMLParser):
            def __init__(self):
                super().__init__()
                self.ids = set()
                self.anchor_refs = []  # (href_target, is_external_file)
            
            def handle_starttag(self, tag, attrs):
                attrs_dict = dict(attrs)
                if 'id' in attrs_dict:
                    self.ids.add(attrs_dict['id'])
                
                if tag == 'a' and 'href' in attrs_dict:
                    href = attrs_dict['href']
                    # Anchor in same file
                    if href.startswith('#'):
                        self.anchor_refs.append((href[1:], fname))
                    # Anchor in external file (e.g., index.html#features)
                    elif '#' in href and not href.startswith(('http://', 'https://', 'mailto:')):
                        parts = href.split('#')
                        if parts[0]:  # Has file part
                            self.anchor_refs.append((parts[1], parts[0]))
        
        parser = AnchorParser()
        with open(fpath) as f:
            parser.feed(f.read())
        
        violations = []
        for anchor_id, ref_file in parser.anchor_refs:
            if ref_file == fname:
                # Same file - anchor_id must be in self.ids
                if anchor_id not in parser.ids:
                    violations.append(f"#{anchor_id} (same file, missing id)")
            else:
                # External file - we need to check that file has this id
                ref_path = BASE_DIR / ref_file
                if ref_path.exists():
                    class IDChecker(HTMLParser):
                        def __init__(self):
                            super().__init__()
                            self.ids = set()
                        def handle_starttag(self, tag, attrs):
                            for k, v in attrs:
                                if k == 'id' and v:
                                    self.ids.add(v)
                    
                    id_checker = IDChecker()
                    with open(ref_path) as f:
                        id_checker.feed(f.read())
                    if anchor_id not in id_checker.ids:
                        violations.append(f"{ref_file}#{anchor_id} (missing in target)")
        
        if violations:
            report("Anchor Integrity", fname, "FAIL", "; ".join(violations[:2]))
        else:
            report("Anchor Integrity", fname, "PASS")
    except Exception as e:
        report("Anchor Integrity", fname, "FAIL", str(e))

# ============ CHECK 5: External Links (target="_blank" + rel="noopener") ============
print("\n5. EXTERNAL LINKS (target=\"_blank\" + rel=\"noopener\")")
print("-" * 100)

for fname in FILES_TO_TEST:
    fpath = BASE_DIR / fname
    try:
        class ExtLinkParser(HTMLParser):
            def __init__(self):
                super().__init__()
                self.violations = []
            
            def handle_starttag(self, tag, attrs):
                if tag == 'a':
                    attrs_dict = dict(attrs)
                    href = attrs_dict.get('href', '')
                    if href.startswith(('http://', 'https://')):
                        has_blank = attrs_dict.get('target') == '_blank'
                        has_noopener = 'noopener' in attrs_dict.get('rel', '')
                        if not (has_blank and has_noopener):
                            self.violations.append(
                                f"{href[:50]} → "
                                f"target={'OK' if has_blank else 'MISSING'}, "
                                f"rel={'OK' if has_noopener else 'MISSING'}"
                            )
        
        parser = ExtLinkParser()
        with open(fpath) as f:
            parser.feed(f.read())
        
        if parser.violations:
            report("External Links", fname, "FAIL", parser.violations[0])
        else:
            report("External Links", fname, "PASS")
    except Exception as e:
        report("External Links", fname, "FAIL", str(e))

# ============ CHECK 6: Images (alt + loading=lazy + decoding=async) ============
print("\n6. IMAGES (alt + loading=\"lazy\" + decoding=\"async\")")
print("-" * 100)

for fname in FILES_TO_TEST:
    fpath = BASE_DIR / fname
    try:
        class ImgParser(HTMLParser):
            def __init__(self):
                super().__init__()
                self.violations = []
            
            def handle_starttag(self, tag, attrs):
                if tag == 'img':
                    attrs_dict = dict(attrs)
                    src = attrs_dict.get('src', '')
                    
                    # Check if relative src
                    if src and not src.startswith(('http://', 'https://', 'data:')):
                        # Must exist on disk
                        img_path = BASE_DIR / src.lstrip("/")
                        if not img_path.exists():
                            self.violations.append(f"{src} (does not exist)")
                            return
                    
                    # Check required attributes
                    missing = []
                    if not attrs_dict.get('alt'):
                        missing.append('alt')
                    if attrs_dict.get('loading') != 'lazy':
                        missing.append('loading=lazy')
                    if attrs_dict.get('decoding') != 'async':
                        missing.append('decoding=async')
                    
                    if missing:
                        self.violations.append(f"{src[:40]} → missing: {', '.join(missing)}")
        
        parser = ImgParser()
        with open(fpath) as f:
            parser.feed(f.read())
        
        if parser.violations:
            report("Images (alt/lazy/async)", fname, "FAIL", parser.violations[0])
        else:
            report("Images (alt/lazy/async)", fname, "PASS")
    except Exception as e:
        report("Images (alt/lazy/async)", fname, "FAIL", str(e))

# ============ CHECK 7: No bare <iframe> ============
print("\n7. NO BARE <iframe> CHECK")
print("-" * 100)

for fname in FILES_TO_TEST:
    fpath = BASE_DIR / fname
    try:
        class IFrameParser(HTMLParser):
            def __init__(self):
                super().__init__()
                self.bare_iframes = []
            
            def handle_starttag(self, tag, attrs):
                if tag == 'iframe':
                    attrs_dict = dict(attrs)
                    # Iframe is "bare" if it lacks security-critical attributes
                    # Look for sandbox, title, src
                    missing = []
                    if 'title' not in attrs_dict:
                        missing.append('title')
                    # Optionally check for sandbox, but in static HTML loaded dynamically it might be OK
                    if missing:
                        self.bare_iframes.append(missing)
        
        parser = IFrameParser()
        with open(fpath) as f:
            parser.feed(f.read())
        
        if parser.bare_iframes:
            report("No Bare iframes", fname, "FAIL", f"Found {len(parser.bare_iframes)} iframe(s) without title")
        else:
            report("No Bare iframes", fname, "PASS")
    except Exception as e:
        report("No Bare iframes", fname, "FAIL", str(e))

# ============ CHECK 8: sitemap.xml parses + https locs ============
print("\n8. SITEMAP (parse + https locs)")
print("-" * 100)
try:
    import xml.etree.ElementTree as ET
    spath = BASE_DIR / "sitemap.xml"
    assert spath.exists(), "sitemap.xml missing"
    tree = ET.parse(spath)
    root = tree.getroot()
    ns = {"sm": "http://www.sitemaps.org/schemas/sitemap/0.9"}
    locs = [e.text.strip() for e in root.findall(".//sm:loc", ns)]
    # fallback if ns not used
    if not locs:
        locs = [e.text.strip() for e in root.findall(".//{http://www.sitemaps.org/schemas/sitemap/0.9}loc")]
    if not locs:
        # no namespace fallback
        locs = [e.text.strip() for e in root.iter() if e.tag.endswith("loc") and e.text]
    assert len(locs) >= 1, "no <loc> found"
    assert len(locs) == 4, f"expected 4 locs got {len(locs)}: {locs}"
    for loc in locs:
        assert loc.startswith("https://"), f"loc not https: {loc}"
        assert not loc.startswith("http://"), f"loc uses http: {loc}"
        p = urlparse(loc)
        assert p.scheme == "https" and p.netloc, f"loc not absolute https: {loc}"
    # canonical exact checks
    assert "https://effrix.com/" in locs, "homepage loc missing/trailing slash"
    assert "https://effrix.com/privacy.html" in locs, "privacy loc missing"
    assert "https://effrix.com/terms.html" in locs, "terms loc missing"
    assert "https://effrix.com/books.html" in locs, "books loc missing"
    report("Sitemap parse + https locs", "sitemap.xml", "PASS")
except AssertionError as e:
    report("Sitemap parse + https locs", "sitemap.xml", "FAIL", str(e))
except Exception as e:
    report("Sitemap parse + https locs", "sitemap.xml", "FAIL", str(e))

# ============ CHECK 9: robots.txt Sitemap + GPTBot/ClaudeBot Allow ============
print("\n9. ROBOTS (Sitemap + AI crawler Allow)")
print("-" * 100)
try:
    rpath = BASE_DIR / "robots.txt"
    assert rpath.exists(), "robots.txt missing"
    txt = rpath.read_text()
    assert "Sitemap: https://effrix.com/sitemap.xml" in txt, "Sitemap line missing"
    # check GPTBot block has Allow: /
    import re as _re
    for bot in ("GPTBot", "ClaudeBot"):
        pat = _re.compile(r"User-agent:\s*" + _re.escape(bot) + r"\s*\nAllow:\s*/", _re.I)
        assert pat.search(txt), f"Allow: / for {bot} missing"
    # parse into User-agent groups and assert every group has all 4 Disallows
    required = ["/test_html_checks.py", "/test_store_links.py", "/.DS_Store", "/amazon-review-screenshots/for-amazon/"]
    groups = {}
    cur_agents = []
    seen_directive = False
    for line in txt.splitlines():
        s = line.strip()
        if not s or s.startswith("#"):
            continue
        low = s.lower()
        if low.startswith("user-agent:"):
            agent = s.split(":", 1)[1].strip()
            if seen_directive:
                cur_agents = [agent]
                seen_directive = False
            else:
                cur_agents.append(agent)
            groups.setdefault(agent, set())
        elif low.startswith("disallow:"):
            seen_directive = True
            val = s.split(":", 1)[1].strip()
            for a in cur_agents:
                groups.setdefault(a, set()).add(val)
        elif low.startswith("allow:") or low.startswith("sitemap:"):
            seen_directive = True
    assert groups, "no User-agent groups found"
    assert "*" in groups, "missing * group"
    for agent, disallows in groups.items():
        for req in required:
            assert req in disallows, f"{agent} missing Disallow: {req} (has: {sorted(disallows)})"
    report("Robots Sitemap + Allow", "robots.txt", "PASS")
except AssertionError as e:
    report("Robots Sitemap + Allow", "robots.txt", "FAIL", str(e))
except Exception as e:
    report("Robots Sitemap + Allow", "robots.txt", "FAIL", str(e))

# ============ CHECK 10: sitemap no http:// locs ============
print("\n10. SITEMAP no http:// locs")
print("-" * 100)
try:
    import xml.etree.ElementTree as _ET2
    spath = BASE_DIR / "sitemap.xml"
    raw = spath.read_text()
    assert "http://" not in raw or raw.count("https://") >= raw.count("http://"), "http:// found"
    # stricter: no <loc>http://
    assert "<loc>http://" not in raw, "sitemap contains http:// loc"
    report("Sitemap no http locs", "sitemap.xml", "PASS")
except AssertionError as e:
    report("Sitemap no http locs", "sitemap.xml", "FAIL", str(e))
except Exception as e:
    report("Sitemap no http locs", "sitemap.xml", "FAIL", str(e))

# ============ CHECK 11: .htaccess (HTTPS redirect, HSTS no preload, -Indexes) ============
print("\n11. HTACCESS (HTTPS redirect + HSTS no preload + -Indexes)")
print("-" * 100)
try:
    hpath = BASE_DIR / ".htaccess"
    assert hpath.exists(), ".htaccess missing"
    ht = hpath.read_text()
    low = ht.lower()
    # HTTPS redirect rule present
    assert "rewriteengine on" in low, "RewriteEngine On missing"
    assert "rewritecond" in low and "https" in low, "HTTPS RewriteCond missing"
    assert "r=301" in low, "R=301 redirect missing"
    assert "https://%{http_host}%{request_uri}" in low, "https://%{HTTP_HOST}%{REQUEST_URI} target missing"
    # proxy-aware: also checks X-Forwarded-Proto
    assert "x-forwarded-proto" in low, "X-Forwarded-Proto check missing (proxy-aware)"
    # HSTS present without preload (check only HSTS directive lines, not comments)
    assert "strict-transport-security" in low, "Strict-Transport-Security missing"
    hsts_lines = [l for l in ht.splitlines() if "strict-transport-security" in l.lower()]
    assert hsts_lines, "Strict-Transport-Security missing"
    assert not any("preload" in l.lower() for l in hsts_lines), "preload must NOT be present in HSTS"
    # Options -Indexes present
    assert "options -indexes" in low, "Options -Indexes missing"
    report(".htaccess checks", ".htaccess", "PASS")
except AssertionError as e:
    report(".htaccess checks", ".htaccess", "FAIL", str(e))
except Exception as e:
    report(".htaccess checks", ".htaccess", "FAIL", str(e))

# ============ Summary ============
print("\n" + "=" * 100)
print(f"SUMMARY: {len(results['pass'])} PASS, {fail_count} FAIL")
if fail_count > 0:
    print("\nFailed checks:")
    for fname, check, detail in results["fail"]:
        print(f"  • {fname} | {check} → {detail}")
    sys.exit(fail_count)
else:
    print("\nALL CHECKS PASS ✓")
    sys.exit(0)
