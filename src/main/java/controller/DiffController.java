package controller;

import core.DiffService;
import utils.I18n;
import view.component.ComparePanel;
import view.component.MessagePanel;

import javax.swing.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Diff 视图控制器（C层）
 * <p>
 * 协调 ComparePanel 的自动 Diff 流程：当 Source/Target 选项卡或内部
 * Request/Response 页签切换时，经防抖 Timer 在后台线程执行 Diff，
 * 比较过程中显示进度条，完成后更新 Diff 展示区。
 */
public class DiffController {

    private static final int AUTO_DIFF_DEBOUNCE_MS = 180;

    private final ComparePanel comparePanel;
    private final DiffService diffService;
    private final ExecutorService diffExecutor;

    public DiffController(ComparePanel comparePanel, DiffService diffService,
                          ExecutorService diffExecutor) {
        this.comparePanel = comparePanel;
        this.diffService = diffService;
        this.diffExecutor = diffExecutor;
    }

    /**
     * 绑定自动 Diff 事件（懒加载）
     * <p>
     * 当 Source/Target 选项卡或内部 Request/Response 页签切换时，自动在后台线程执行 Diff，
     * 比较过程中显示进度条，完成后更新 Diff 展示区。
     */
    public void bind() {
        AtomicInteger requestVersion = new AtomicInteger();
        AtomicReference<DiffContext> pendingContextRef = new AtomicReference<>();
        AtomicBoolean diffRunning = new AtomicBoolean(false);

        Runnable scheduleLatestDiff = new Runnable() {
            @Override
            public void run() {
                if (!diffRunning.compareAndSet(false, true)) {
                    return;
                }

                DiffContext context = pendingContextRef.getAndSet(null);
                if (context == null) {
                    diffRunning.set(false);
                    comparePanel.hideProgress();
                    return;
                }

                comparePanel.showProgress();
                boolean sideBySide = comparePanel.isSideBySideMode();
                diffExecutor.submit(() -> {
                    String diffBody = "";
                    DiffService.SideBySideDiffResult sideBySideResult = null;
                    Exception error = null;
                    try {
                        if (sideBySide) {
                            sideBySideResult = diffService.diffSideBySide(
                                    context.sourceText(), context.targetText());
                        } else {
                            diffBody = diffService.diff(context.sourceText(), context.targetText());
                        }
                    } catch (Exception ex) {
                        error = ex;
                    }

                    String finalDiffBody = diffBody;
                    DiffService.SideBySideDiffResult finalSideBySideResult = sideBySideResult;
                    Exception finalError = error;
                    SwingUtilities.invokeLater(() -> {
                        try {
                            if (context.version() == requestVersion.get()) {
                                if (finalError != null) {
                                    comparePanel.setDiffContent("<html><body><p>Diff error: "
                                            + finalError.getMessage() + "</p></body></html>");
                                } else if (sideBySide && finalSideBySideResult != null) {
                                    String wrapL = buildSideBySideHtml(finalSideBySideResult.leftHtml());
                                    String wrapR = buildSideBySideHtml(finalSideBySideResult.rightHtml());
                                    comparePanel.setDiffContentSideBySide(wrapL, wrapR);
                                } else {
                                    comparePanel.setDiffContent(buildDiffHtml(
                                            context.sourceName(), context.targetName(),
                                            context.tabType(), finalDiffBody));
                                }
                            }
                        } finally {
                            diffRunning.set(false);
                            if (pendingContextRef.get() != null) {
                                this.run();
                            } else {
                                comparePanel.hideProgress();
                            }
                        }
                    });
                });
            }
        };

        Timer debounceTimer = new Timer(AUTO_DIFF_DEBOUNCE_MS, e -> {
            DiffContext context = pendingContextRef.get();
            if (context == null) {
                return;
            }

            comparePanel.showProgress();
            scheduleLatestDiff.run();
        });
        debounceTimer.setRepeats(false);

        comparePanel.setDiffCallback(panel -> {
            I18n i18n = I18n.getInstance();
            MessagePanel sourcePanel = panel.getSelectedSourcePanel();
            MessagePanel targetPanel = panel.getSelectedTargetPanel();
            if (sourcePanel == null || targetPanel == null) {
                debounceTimer.stop();
                requestVersion.incrementAndGet();
                pendingContextRef.set(null);
                panel.hideProgress();
                panel.setDiffContent("<html><body><p>"
                        + i18n.text("compare", "message.selectSourceTarget")
                        + "</p></body></html>");
                return;
            }

            String sourceName = panel.getSelectedSourceName();
            String targetName = panel.getSelectedTargetName();
            MessagePanel.MessageView view = sourcePanel.getSelectedView();
            String tabType = switch (view) {
                case REQUEST -> i18n.text("message", "tab.request");
                case RESPONSE -> i18n.text("message", "tab.response");
                case COMBINED -> i18n.text("message", "tab.combined");
            };

            String sourceText = sourcePanel.getSelectedText();
            String targetText = targetPanel.getSelectedText();

            pendingContextRef.set(new DiffContext(
                    requestVersion.incrementAndGet(),
                    sourceName,
                    targetName,
                    tabType,
                    sourceText,
                    targetText));
            debounceTimer.restart();
        });
    }

    private String buildDiffHtml(String sourceName, String targetName, String tabType, String diffBody) {
        I18n i18n = I18n.getInstance();
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Courier New;font-size:10pt;'>");
        html.append("<b>")
                .append(i18n.format("compare", "title.diff",
                        i18n.translateAuthObjectName(sourceName), tabType,
                        i18n.translateAuthObjectName(targetName), tabType))
                .append("</b><br>");
        if (diffBody.isEmpty()) {
            html.append("<br><span style='color:green;'>")
                    .append(i18n.text("compare", "message.noDiff"))
                    .append("</span>");
        } else {
            html.append(diffBody);
        }
        html.append("</body></html>");
        return html.toString();
    }

    private String buildSideBySideHtml(String bodyHtml) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Courier New;font-size:10pt;'>");
        html.append(bodyHtml);
        html.append("</body></html>");
        return html.toString();
    }

    private record DiffContext(int version, String sourceName, String targetName,
                               String tabType, String sourceText, String targetText) {
    }
}
