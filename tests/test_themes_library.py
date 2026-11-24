"""tools/themes.sh sends theme folders into the launcher's theme library (Config.dir/themes/<name>/), which the
Theme row in Launcher settings applies from. It writes the way theme.sh does (provider first, cp as the
fallback, a check that each file landed), never touches the active theme and never restarts Home.

The harness is tests/test_theme_push_ownership.py's: a fake tv.py whose shell is /bin/sh on this machine and a
stub `content`. The cases need bash and skip without it.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import test_theme_push_ownership as push  # noqa: E402  (its harness, not its tests)

ROOT = Path(__file__).resolve().parents[1]


class ThemesLibraryTest(unittest.TestCase):
    def setUp(self):
        push.ThemePushTest.setUp(self)
        shutil.copy2(ROOT / "tools" / "themes.sh", self.tools / "themes.sh")
        (self.tools / "tv.py").write_text(push.FAKE_TV.replace('    Device().shell(" ".join(args))',
                                                               '    sys.stdout.write(Device().shell(" ".join(args)))'))
        cp = self.bin / "cp"
        cp.write_text('#!/bin/sh\nif [ -n "$FAKE_NO_CP" ]; then exit 1; fi\nexec /bin/cp "$@"\n')
        cp.chmod(0o755)
        self.themes = self.base / "themes"
        self.gamer = self.make_theme("gamer", {"columns": 5}, wallpapers=["a.jpg", "b.jpg"], screenshot=True)
        self.cine = self.make_theme("cine", {"columns": 6}, single=True)

    def make_theme(self, name, config, wallpapers=(), single=False, screenshot=False):
        folder = self.themes / name
        folder.mkdir(parents=True)
        (folder / "config.json").write_text(json.dumps(config))
        if single:
            (folder / "wallpaper.jpg").write_bytes(b"\xff\xd8single")
        if screenshot:
            (folder / "screenshot.jpg").write_bytes(b"\xff\xd8shot")
        if wallpapers:
            (folder / "wallpapers").mkdir()
            for w in wallpapers:
                (folder / "wallpapers" / w).write_bytes(b"\xff\xd8" + w.encode())
            (folder / "wallpapers" / "captions.txt").write_text("a.jpg|A\n")
        return folder

    def run_themes(self, *args, **env):
        if os.name != "posix" or not shutil.which("bash"):
            raise unittest.SkipTest("themes.sh is a POSIX script; it needs bash")
        full = {k: v for k, v in os.environ.items() if k != "TV_HOST"}
        full.update({"TV_HOST": "192.0.2.10", "FAKE_LOG": str(self.log), "FAKE_DIR": str(self.device),
                     "FAKE_STAGE": str(self.stage), "FAKE_CONTENT_LOG": str(self.content_log),
                     "FAKE_SHELL_DIRS": str(self.shell_dirs),
                     "PATH": f"{self.bin}:{full.get('PATH', '')}"}, **env)
        return subprocess.run(["bash", str(self.tools / "themes.sh"), *args], env=full,
                              capture_output=True, text=True, timeout=300)

    def library(self, *parts):
        return self.device.joinpath("themes", *parts)

    def test_folders_land_in_the_library_and_nothing_is_applied(self):
        result = self.run_themes(str(self.gamer), str(self.cine))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(json.loads(self.library("gamer", "config.json").read_text()), {"columns": 5})
        self.assertEqual(self.library("gamer", "screenshot.jpg").read_bytes(), b"\xff\xd8shot")
        self.assertEqual(self.library("gamer", "wallpapers", "b.jpg").read_bytes(), b"\xff\xd8b.jpg")
        self.assertEqual(self.library("gamer", "wallpapers", "captions.txt").read_text(), "a.jpg|A\n")
        self.assertEqual(self.library("cine", "wallpaper.jpg").read_bytes(), b"\xff\xd8single")
        # The active theme and Home are left alone.
        self.assertFalse((self.device / "config.json").exists())
        self.assertFalse((self.device / "theme_revision").exists())
        self.assertFalse(any("am " in c or "force-stop" in c for c in push.ThemePushTest.shell_commands(self)))
        self.assertIn("added gamer", result.stdout)

    def test_all_sends_every_theme_folder(self):
        result = self.run_themes("--all")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue(self.library("gamer", "config.json").is_file())
        self.assertTrue(self.library("cine", "config.json").is_file())

    def test_sending_again_drops_pictures_the_folder_no_longer_has(self):
        self.assertEqual(self.run_themes(str(self.gamer)).returncode, 0)
        (self.gamer / "wallpapers" / "b.jpg").unlink()
        result = self.run_themes(str(self.gamer))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue(self.library("gamer", "wallpapers", "a.jpg").exists())
        self.assertFalse(self.library("gamer", "wallpapers", "b.jpg").exists())

    def test_the_cp_fallback_writes_the_library_too(self):
        result = self.run_themes(str(self.cine), FAKE_NO_CONTENT_WRITE="1")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue(self.library("cine", "wallpaper.jpg").is_file())

    def test_a_file_both_writes_refused_fails_with_1(self):
        result = self.run_themes(str(self.cine), FAKE_NO_CONTENT_WRITE="1", FAKE_NO_CP="1")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("neither the provider write nor the copy of themes/cine/config.json", result.stderr)

    def test_a_bad_folder_name_sends_nothing(self):
        bad = self.make_theme("Bad Name", {"columns": 4})
        result = self.run_themes(str(self.gamer), str(bad))
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("Nothing was sent", result.stderr)
        self.assertFalse(self.library("gamer").exists())

    def test_a_config_that_is_not_an_object_sends_nothing(self):
        (self.cine / "config.json").write_text("[1, 2]")
        result = self.run_themes(str(self.cine))
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertFalse(self.library("cine").exists())

    def test_remove_and_list(self):
        self.assertEqual(self.run_themes(str(self.gamer), str(self.cine)).returncode, 0)
        listed = self.run_themes("--list")
        self.assertEqual(listed.returncode, 0, listed.stdout + listed.stderr)
        self.assertEqual(listed.stdout.split(), ["cine", "gamer"])
        removed = self.run_themes("--remove", "gamer")
        self.assertEqual(removed.returncode, 0, removed.stdout + removed.stderr)
        self.assertFalse(self.library("gamer").exists())
        self.assertTrue(self.library("cine").exists())

    def test_remove_refuses_a_path(self):
        result = self.run_themes("--remove", "../config.json")
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertEqual(push.ThemePushTest.shell_commands(self), [])

    def test_command_lines_it_cannot_read(self):
        for args in ([], ["--list", "x"], ["--remove"], ["--bogus"], ["--all", "themes/x"]):
            result = self.run_themes(*args)
            self.assertEqual(result.returncode, 2, f"{args}: {result.stdout}{result.stderr}")
        self.assertEqual(push.ThemePushTest.shell_commands(self), [])


if __name__ == "__main__":
    unittest.main()
