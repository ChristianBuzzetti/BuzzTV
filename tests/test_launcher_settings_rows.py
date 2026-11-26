"""docs/launcher.md: the Launcher settings section names every row the settings screen has.

The section moved out of the README into docs/launcher.md, which the README's launcher section links.

BuzzTV builds the settings screens in code (LauncherSettingsActivity), so the rows are read from it, not from
a list kept here: every hub card (`bigCard`, `smallRow`) and every row on a category screen (`toggle`,
`labeledSegment`, `actionRow(getString(...))`) takes its label from a string resource. A row added without a
line in the guide fails in CI, while rewording a guide line stays free as long as the row's own label - the
text the TV shows - is still there. The numbers next to the rows (4 to 7, 0 to 3, the four dock opacities)
come from the same file.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ACTIVITY = ROOT / "launcher/app/src/main/java/com/gathin/tvhome/LauncherSettingsActivity.java"
STRINGS = ROOT / "launcher/app/src/main/res/values/strings.xml"
GUIDE = ROOT / "docs" / "launcher.md"

SUMMARY = "Launcher settings and the default home"
ROW_CALLS = (
    r"bigCard\([^;]*?R\.string\.([a-z_0-9]+)",
    r"smallRow\([^;]*?R\.string\.([a-z_0-9]+)",
    r"toggle\(\s*R\.string\.([a-z_0-9]+)",
    r"labeledSegment\(\s*R\.string\.([a-z_0-9]+)",
    r"actionRow\(\s*getString\(R\.string\.([a-z_0-9]+)",
)


def english_strings(text):
    """name -> value for every <string> in res/values/strings.xml (the English text Android shows)."""
    out = {}
    for match in re.finditer(r"<string name=\"([a-z_0-9]+)\"[^>]*>(.*?)</string>", text, re.S):
        out[match.group(1)] = match.group(2).strip()
    return out


def normalise(text):
    """One line, so a label the README wraps across two lines still matches."""
    return re.sub(r"\s+", " ", text.replace("**", "").replace("\\", "")).strip()


def row_label(name, strings):
    """A resource name as the row's label: 'animations_state' ("Animations: %1$s") is 'Animations'."""
    value = strings.get(name)
    if value is None:
        return None
    value = re.sub(r"%\d\$s", "", value)
    return value.strip().rstrip(":").strip() or None


def row_labels(activity_text, strings):
    """Every label the settings screens show, in code order, as [(resource, label)], each once."""
    found = []
    for pattern in ROW_CALLS:
        for match in re.finditer(pattern, activity_text, re.S):
            found.append((match.start(), match.group(1)))
    rows, seen = [], set()
    for _, name in sorted(found):
        label = row_label(name, strings)
        if label and label not in seen:
            seen.add(label)
            rows.append((name, label))
    return rows


def missing_rows(section, rows):
    """The (id, label) rows the section does not name."""
    seen = normalise(section).lower()
    return [(row_id, label) for row_id, label in rows if label.lower() not in seen]


def segment(label, activity_text):
    """The choices a labeledSegment row offers, as written in the code."""
    match = re.search(r"labeledSegment\(\s*R\.string\.%s\s*,\s*new String\[\]\{([^}]*)\}" % label, activity_text)
    return re.findall(r'"([^"]+)"', match.group(1)) if match else []


def detail_section(text, summary):
    """The text under one `## summary` heading up to the next `## `, or ''."""
    start = text.find("## %s\n" % summary)
    if start < 0:
        return ""
    end = text.find("\n## ", start + 3)
    return text[start:end if end > 0 else len(text)]


class ScreenRows(unittest.TestCase):
    """The guide against the settings screens it describes."""

    def setUp(self):
        self.activity_text = ACTIVITY.read_text(encoding="utf-8")
        self.strings = english_strings(STRINGS.read_text(encoding="utf-8"))
        self.rows = row_labels(self.activity_text, self.strings)
        self.section = detail_section(GUIDE.read_text(encoding="utf-8"), SUMMARY)

    def test_screen_rows_are_read(self):
        self.assertGreaterEqual(len(self.rows), 12, self.rows)
        for row_id, label in self.rows:
            self.assertTrue(label.strip(), row_id)

    def test_guide_names_every_row(self):
        self.assertTrue(self.section, "docs/launcher.md has no '%s' section" % SUMMARY)
        self.assertEqual(missing_rows(self.section, self.rows), [])

    def test_guide_names_the_numbers_under_each_row(self):
        columns = segment("columns_label", self.activity_text)
        under_dock = segment("rows_below_dock_label", self.activity_text)
        self.assertEqual(columns, ["4", "5", "6", "7"])
        self.assertEqual(under_dock, ["0", "1", "2", "3"])
        opacities = re.search(r"DOCK_OPACITIES\s*=\s*\{([0-9,\s]+)\}", self.activity_text).group(1)
        opacities = [value.strip() for value in opacities.split(",") if value.strip()]
        section = normalise(self.section)
        self.assertIn("%s, %s, %s or %s" % tuple(columns), section)
        self.assertIn("%s to %s" % (under_dock[0], under_dock[-1]), section)
        self.assertIn("%s%%, %s%%, %s%% or %s%%" % tuple(opacities), section)

    def test_a_row_the_guide_leaves_out_is_reported(self):
        rows = [("reorder", "Reorder apps"), ("walls", "Wallpaper")]
        self.assertEqual(missing_rows("- **Reorder apps** only with the button.", rows),
                         [("walls", "Wallpaper")])

    def test_a_row_named_in_another_section_does_not_count(self):
        readme = ("## %s\n\nNothing here.\n\n"
                  "## Elsewhere\n\n- **Wallpaper** this is not the settings page.\n" % SUMMARY)
        section = detail_section(readme, SUMMARY)
        self.assertIn("Nothing here", section)
        self.assertNotIn("Elsewhere", section)
        self.assertEqual(missing_rows(section, [("w", "Wallpaper")]), [("w", "Wallpaper")])

    def test_a_placeholder_is_not_part_of_the_label(self):
        strings = {"animations_state": "Animations: %1$s"}
        self.assertEqual(row_label("animations_state", strings), "Animations")

    def test_rows_are_read_from_each_kind_of_call(self):
        code = ('bigCard(R.drawable.x, 1, 2, R.string.a, v, h, go); toggle(R.string.b, on, f);'
                ' labeledSegment(R.string.c, new String[]{"1", "2"}, 0, p); actionRow(getString(R.string.d), "", g);'
                ' smallRow(R.drawable.y, R.string.e, v, go);')
        strings = {"a": "A", "b": "B", "c": "C", "d": "D", "e": "E"}
        self.assertEqual([label for _, label in row_labels(code, strings)], ["A", "B", "C", "D", "E"])
        self.assertEqual(segment("c", code), ["1", "2"])


if __name__ == "__main__":
    unittest.main()
