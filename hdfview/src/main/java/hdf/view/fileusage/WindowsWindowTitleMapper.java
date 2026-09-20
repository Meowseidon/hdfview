package hdf.view.fileusage;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/** Pure mapping logic for the visible, titled top-level windows of one PID. */
public final class WindowsWindowTitleMapper {
    private WindowsWindowTitleMapper() {
    }

    public record WindowSnapshot(long processId, boolean visible, String title) {
    }

    public static List<String> visibleTitlesFor(
            long processId, Collection<WindowSnapshot> windows) {
        if (windows == null || windows.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> titles = new LinkedHashSet<>();
        for (WindowSnapshot window : windows) {
            if (window == null || window.processId() != processId || !window.visible()) {
                continue;
            }
            String title = window.title();
            if (title != null && !title.isBlank()) {
                titles.add(title.trim());
            }
        }
        return List.copyOf(titles);
    }
}
