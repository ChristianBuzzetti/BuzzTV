package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The apps a custom Home layout shows: the dock's first, then the rest of the grid, without repeats. */
final class HomeApps {
    private HomeApps() {}

    static List<AppAdapter.Entry> pick(HomeLayoutHost host, int max) {
        List<AppAdapter.Entry> out = new ArrayList<>(max);
        Set<String> seen = new HashSet<>();
        add(out, seen, host.dockApps(), max);
        if (out.isEmpty()) add(out, seen, host.allApps(), max);
        return out;
    }

    static List<AppAdapter.Entry> pickFilled(HomeLayoutHost host, int max) {
        List<AppAdapter.Entry> out = new ArrayList<>(max);
        Set<String> seen = new HashSet<>();
        add(out, seen, host.dockApps(), max);
        add(out, seen, host.allApps(), max);
        return out;
    }

    private static void add(List<AppAdapter.Entry> out, Set<String> seen, List<AppAdapter.Entry> from, int max) {
        if (from == null) return;
        for (AppAdapter.Entry e : from) {
            if (out.size() >= max) return;
            if (e == null || e.kind == AppAdapter.Entry.EMPTY || e.component == null) continue;
            if (!seen.add(e.component.flattenToString())) continue;
            out.add(e);
        }
    }
}
