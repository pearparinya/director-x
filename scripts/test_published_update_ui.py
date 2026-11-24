"""Exercise the published Android updater on a disposable rooted emulator."""
import base64
import hashlib
import json
import re
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET

PACKAGE = "com.pearparinya.automovie"
ROOT = "/data/data/" + PACKAGE
API = "https://api.github.com/repos/pearparinya/director-x/releases"


def adb(*args, data=None):
    return subprocess.check_output(["adb", *args], input=data)


def click(label, seconds=90):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        adb("shell", "uiautomator", "dump", "/sdcard/window.xml")
        tree = ET.fromstring(adb("exec-out", "cat", "/sdcard/window.xml"))
        for node in tree.iter("node"):
            if label.casefold() in ((node.get("text") or "").casefold(), (node.get("content-desc") or "").casefold()):
                x, y, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
                adb("shell", "input", "tap", str((x + right)//2), str((y + bottom)//2))
                return
        time.sleep(1)
    visible = [n.get("text") for n in tree.iter("node") if n.get("text")]
    raise RuntimeError(f"Missing {label!r}, visible text: {visible}")


def release(path):
    with urllib.request.urlopen(API + path) as response:
        return json.load(response)


def asset(info):
    return next(a for a in info["assets"] if a["name"].endswith(".apk"))


latest = release("/latest")
history = release("?per_page=100")
older = next(r for r in history if not r["prerelease"] and not r["draft"]
             and r["published_at"] < latest["published_at"] and any(a["name"].endswith(".apk") for a in r["assets"]))
previous = asset(older)
urllib.request.urlretrieve(previous["browser_download_url"], "/tmp/previous.apk")
with open("/tmp/previous.apk", "rb") as f:
    assert "sha256:" + hashlib.file_digest(f, "sha256").hexdigest() == previous["digest"]
for attempt in range(4):
    adb("wait-for-device")
    subprocess.run(["adb", "root"], check=False)
    time.sleep(2)
    adb("wait-for-device")
    if adb("shell", "id", "-u").strip() == b"0":
        break
else:
    raise RuntimeError("Rooted test emulator unavailable")
adb("install", "/tmp/previous.apk")
# Suppress only the automatic download reminder so the manual path is exercised.
build = latest["tag_name"].rsplit("build", 1)[1]
assert build.isdigit()
prefs = ('<map><long name="auto_download_build" value="%s"/><long name="auto_download_at" value="%d"/></map>'
         % (build, time.time()*1000)).encode()
fixtures = {
    "files/studio/qa/story.json": b'{"title":"QA","episodes":[]}',
    "files/studio/qa/image.png": base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9YL9ZQAAAABJRU5ErkJggg=="),
    "files/character_masters/qa.master": b"qa-master",

}
owner = adb("shell", "stat", "-c", "%u:%g", ROOT).decode().strip()
setting_path = "shared_prefs/studio.xml"
setting = b'<map><boolean name="detailed_image_profiles" value="true"/></map>'
fixtures["files/character_masters/qa.master"] = fixtures["files/studio/qa/image.png"]
for path, data in {**fixtures, setting_path: setting, "shared_prefs/director_x_updater.xml": prefs}.items():
    target = ROOT + "/" + path
    adb("shell", "mkdir", "-p", target.rsplit("/", 1)[0])
    adb("shell", "cat > " + target, data=data)
for folder in ("files", "shared_prefs"):
    adb("shell", "chown", "-R", owner, ROOT + "/" + folder)
adb("shell", "restorecon", "-RF", ROOT)
adb("shell", "appops", "set", PACKAGE, "REQUEST_INSTALL_PACKAGES", "allow")
adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.StudioActivity")
click("อัปเดตใหม่")
click("✅ อัปเดต")
click("Update", 180)
for attempt in range(60):
    if re.search(rb"versionCode=" + build.encode() + rb"\b", adb("shell", "dumpsys", "package", PACKAGE)):
        break
    time.sleep(1)
else:
    raise RuntimeError("Installer did not upgrade the package")
adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.StudioActivity")
for path, expected in fixtures.items():
    assert adb("exec-out", "cat", ROOT + "/" + path) == expected, path
saved = ET.fromstring(adb("exec-out", "cat", ROOT + "/" + setting_path))
assert saved.find("boolean[@name='detailed_image_profiles']").get("value") == "true"
print("Verified", older["tag_name"], "to", latest["tag_name"], "through the manual updater and Android installer")
