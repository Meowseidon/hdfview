package hdf.view.fileusage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WindowsWindowTitleMapperTest {
    @Test
    void mapsOnlyVisibleTitledWindowsBelongingToTheExactPid() {
        List<WindowsWindowTitleMapper.WindowSnapshot> windows = List.of(
                new WindowsWindowTitleMapper.WindowSnapshot(41, true, "First window"),
                new WindowsWindowTitleMapper.WindowSnapshot(41, false, "Hidden window"),
                new WindowsWindowTitleMapper.WindowSnapshot(41, true, "   "),
                new WindowsWindowTitleMapper.WindowSnapshot(42, true, "Other process"),
                new WindowsWindowTitleMapper.WindowSnapshot(41, true, "First window"),
                new WindowsWindowTitleMapper.WindowSnapshot(41, true, "Second window"));

        assertEquals(
                List.of("First window", "Second window"),
                WindowsWindowTitleMapper.visibleTitlesFor(41, windows));
    }
}
