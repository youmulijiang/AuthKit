package view.component;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import utils.I18n;

import javax.swing.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataTablePanelTest {

    @Test
    @DisplayName("DataTable 应支持多行选择并返回 model 行索引")
    void table_shouldSupportMultipleRowSelection() throws Exception {
        DataTablePanel panel = createPanelWithRows();

        SwingUtilities.invokeAndWait(() -> panel.getTableData().setRowSelectionInterval(0, 1));

        assertEquals(List.of(0, 1), panel.getSelectedRows());
        assertEquals(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                panel.getTableData().getSelectionModel().getSelectionMode());
    }

    @Test
    @DisplayName("右键菜单动作应把选中 model 行交给协调层处理")
    void contextMenuAction_shouldPassSelectedRowsToHandler() throws Exception {
        DataTablePanel panel = createPanelWithRows();
        List<Integer> exportedRows = new ArrayList<>();
        panel.setSelectionActionHandler(new DataTablePanel.SelectionActionHandler() {
            @Override
            public void exportCsv(List<Integer> modelRows) {
                exportedRows.addAll(modelRows);
            }

            @Override
            public void exportHtml(List<Integer> modelRows) {
            }

            @Override
            public void copyUrls(List<Integer> modelRows) {
            }
        });

        SwingUtilities.invokeAndWait(() -> {
            panel.getTableData().setRowSelectionInterval(0, 1);
            getField(panel, "itemExportCsv", JMenuItem.class).doClick();
        });

        assertEquals(List.of(0, 1), exportedRows);
    }

    @Test
    @DisplayName("DataTable 右键菜单中文文案不应乱码")
    void contextMenuText_shouldReadChineseWithoutMojibake() {
        I18n i18n = I18n.getInstance();
        I18n.Language originalLanguage = i18n.getCurrentLanguage();
        try {
            i18n.setLanguage(I18n.Language.CHINESE);

            assertEquals("\u5bfc\u51fa\u9009\u4e2d\u7ed3\u679c\u4e3a CSV",
                    i18n.text("data_table", "menu.exportCsv"));
            assertEquals("\u590d\u5236\u9009\u4e2d\u7684 URL",
                    i18n.text("data_table", "menu.copyUrls"));
        } finally {
            i18n.setLanguage(originalLanguage);
        }
    }

    @Test
    @DisplayName("编号列应按数字排序且选中行仍映射到正确的 model 对象")
    void idColumn_shouldSortByNumberAndKeepModelMapping() throws Exception {
        DataTablePanel panel = createPanelWithUnsortedRows();
        int[] selectedModelRow = {-1};

        SwingUtilities.invokeAndWait(() -> {
            JTable table = panel.getTableData();
            table.getRowSorter().toggleSortOrder(0);

            assertEquals(1, table.getValueAt(0, 0));
            assertEquals(2, table.getValueAt(1, 0));
            assertEquals(10, table.getValueAt(2, 0));
            assertEquals(2, table.convertRowIndexToModel(0));

            table.setRowSelectionInterval(0, 0);
            selectedModelRow[0] = panel.getSelectedRow();
        });

        assertEquals(2, selectedModelRow[0]);
    }

    @Test
    @DisplayName("字符列应按字符串长度排序")
    void textColumn_shouldSortByTextLength() throws Exception {
        DataTablePanel panel = createPanelWithUnsortedRows();

        SwingUtilities.invokeAndWait(() -> {
            JTable table = panel.getTableData();
            table.getRowSorter().toggleSortOrder(1);

            assertEquals("GET", table.getValueAt(0, 1));
            assertEquals("POST", table.getValueAt(1, 1));
            assertEquals("DELETE", table.getValueAt(2, 1));
        });
    }

    @Test
    @DisplayName("同一列表头第三次点击应取消排序并恢复 model 顺序")
    void headerThirdClick_shouldClearSortKeys() throws Exception {
        DataTablePanel panel = createPanelWithUnsortedRows();

        SwingUtilities.invokeAndWait(() -> {
            JTable table = panel.getTableData();
            RowSorter<?> sorter = table.getRowSorter();
            sorter.toggleSortOrder(0);
            sorter.toggleSortOrder(0);
            sorter.toggleSortOrder(0);

            assertTrue(sorter.getSortKeys().isEmpty());
            assertEquals(10, table.getValueAt(0, 0));
            assertEquals(2, table.getValueAt(1, 0));
            assertEquals(1, table.getValueAt(2, 0));
        });
    }

    private DataTablePanel createPanelWithRows() throws Exception {
        DataTablePanel panel = new DataTablePanel.Builder().build();
        SwingUtilities.invokeAndWait(() -> {
            panel.addRow(new Object[]{1, "GET", "https://a.test", 100, 403});
            panel.addRow(new Object[]{2, "POST", "https://b.test", 200, 200});
        });
        return panel;
    }

    private DataTablePanel createPanelWithUnsortedRows() throws Exception {
        DataTablePanel panel = new DataTablePanel.Builder().build();
        SwingUtilities.invokeAndWait(() -> {
            panel.addRow(new Object[]{10, "DELETE", "https://ccc.test", 300, 403});
            panel.addRow(new Object[]{2, "POST", "https://bb.test", 200, 200});
            panel.addRow(new Object[]{1, "GET", "https://a.test", 100, 403});
        });
        return panel;
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String fieldName, Class<T> type) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
