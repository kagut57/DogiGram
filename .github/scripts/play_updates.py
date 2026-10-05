import datetime
import html
import json
import pathlib
import re
import sys
import urllib.request

PACKAGE = "com.dogigram.app"
LISTING_URL = f"https://play.google.com/store/apps/details?id={PACKAGE}&hl=en&gl=US"
UPDATES_FILE = pathlib.Path(__file__).resolve().parents[2] / "docs" / "updates.json"


def fetch_listing():
    request = urllib.request.Request(
        LISTING_URL,
        headers={
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36",
            "Accept-Language": "en-US,en;q=0.9",
        },
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read().decode("utf-8", errors="replace")


def js_string(raw):
    return json.loads('"' + raw + '"')


def parse_version(page):
    match = re.search(r'\[\[\["(\d+(?:\.\d+)+)"\]\],\[\[\[\d+\]\],\[\[\[\d+,"[\d.]+"\]\]\]\]', page)
    if match:
        return match.group(1)
    match = re.search(r'"141":\[\[\["([^"]+)"\]\]', page)
    return match.group(1) if match else None


def parse_min_android(page):
    match = re.search(r'\[\[\["\d+(?:\.\d+)+"\]\],\[\[\[\d+\]\],\[\[\[\d+,"([\d.]+)"\]\]\]\]', page)
    return match.group(1) if match else None


def parse_date(page):
    match = re.search(r'\[\["([A-Z][a-z]{2} \d{1,2}, \d{4})",\[(\d{9,11}),', page)
    if match:
        stamp = datetime.datetime.fromtimestamp(int(match.group(2)), tz=datetime.timezone.utc)
        return stamp.date().isoformat()
    match = re.search(r"Updated on</div><div[^>]*>([^<]+)</div>", page)
    if match:
        return datetime.datetime.strptime(match.group(1).strip(), "%b %d, %Y").date().isoformat()
    return None


def parse_notes(page):
    raw = None
    match = re.search(r'"145":\[null,\[null,"((?:[^"\\]|\\.)*)"\]\]', page)
    if match:
        raw = js_string(match.group(1))
    else:
        blocks = re.findall(r'<div itemprop="description">(.*?)</div>', page, re.S)
        if blocks:
            raw = blocks[-1]
    if not raw:
        return []
    notes = []
    for part in re.split(r"<br\s*/?>|\n", raw):
        text = html.unescape(re.sub(r"<[^>]+>", "", part)).strip()
        text = text.lstrip("•·-*• ").strip()
        if text:
            notes.append(text)
    return notes


def main():
    page = fetch_listing()
    version = parse_version(page)
    if not version:
        print("Could not find the version on the Play listing; leaving updates.json unchanged.")
        return 0
    entry = {
        "version": version,
        "date": parse_date(page) or datetime.date.today().isoformat(),
        "notes": parse_notes(page),
    }
    min_android = parse_min_android(page)

    data = {"app": {}, "updates": []}
    if UPDATES_FILE.exists():
        data = json.loads(UPDATES_FILE.read_text(encoding="utf-8"))
    data.setdefault("app", {})
    data.setdefault("updates", [])

    changed = False
    if min_android and data["app"].get("minAndroid") != min_android:
        data["app"]["minAndroid"] = min_android
        changed = True

    existing = next((item for item in data["updates"] if item.get("version") == version), None)
    if existing is None:
        data["updates"].insert(0, entry)
        changed = True
        print(f"Added {version} from {entry['date']}.")
    elif entry["notes"] and existing.get("notes") != entry["notes"]:
        existing["notes"] = entry["notes"]
        changed = True
        print(f"Updated the notes for {version}.")

    if changed:
        data["app"]["checked"] = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat()
        UPDATES_FILE.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    else:
        print(f"No new version. Latest is still {version}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
