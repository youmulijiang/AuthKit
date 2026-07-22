package utils;

import java.io.File;
import java.nio.file.Path;

/**
 * 文件工具类（纯函数，无状态）
 * 提供文件路径标准化等通用方法。
 */
public final class FileUtils {

    private FileUtils() {
    }

    /**
     * 确保文件路径以指定后缀结尾；若缺少则自动追加。
     * <p>
     * 例如：("report", "csv") → Path("report.csv")，("report.csv", "csv") → Path("report.csv")
     *
     * @param selectedFile 用户选择的文件对象
     * @param extension    期望的后缀（不含点号，如 "csv"、"html"）
     * @return 带有正确后缀的 {@link Path}
     */
    public static Path ensureExtension(File selectedFile, String extension) {
        String path = selectedFile.getPath();
        if (!path.toLowerCase().endsWith("." + extension)) {
            path = path + "." + extension;
        }
        return Path.of(path);
    }
}
