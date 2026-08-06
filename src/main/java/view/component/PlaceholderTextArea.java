package view.component;

import javax.swing.*;
import java.awt.*;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;

/**
 * 支持空文本占位提示的 JTextArea，提示只用于展示，不参与 getText()。
 */
public class PlaceholderTextArea extends JTextArea {

    private String placeholder = "";
    private Color placeholderColor = new Color(150, 150, 150);

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder != null ? placeholder : "";
        repaint();
    }

    public void setPlaceholderColor(Color placeholderColor) {
        this.placeholderColor = placeholderColor != null ? placeholderColor : new Color(150, 150, 150);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (!getText().isEmpty() || placeholder.isEmpty()) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setColor(placeholderColor);
            g2.setFont(getFont());
            FontMetrics metrics = g2.getFontMetrics();
            Insets insets = getInsets();
            int x = insets.left;
            int y = insets.top + metrics.getAscent();
            int maxWidth = getWidth() - insets.left - insets.right;
            for (String line : wrapPlaceholderLines(metrics, maxWidth)) {
                g2.drawString(line, x, y);
                y += metrics.getHeight();
            }
        } finally {
            g2.dispose();
        }
    }

    private List<String> wrapPlaceholderLines(FontMetrics metrics, int maxWidth) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : placeholder.split("\n", -1)) {
            wrapParagraph(paragraph, metrics, maxWidth, lines);
        }
        return lines;
    }

    private void wrapParagraph(String paragraph, FontMetrics metrics, int maxWidth, List<String> lines) {
        if (!getLineWrap() || maxWidth <= 0 || metrics.stringWidth(paragraph) <= maxWidth) {
            lines.add(paragraph);
            return;
        }

        BreakIterator iterator = getWrapStyleWord()
                ? BreakIterator.getLineInstance(getLocale())
                : BreakIterator.getCharacterInstance(getLocale());
        iterator.setText(paragraph);
        StringBuilder current = new StringBuilder();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String part = paragraph.substring(start, end);
            if (!current.isEmpty() && metrics.stringWidth(current + part) > maxWidth) {
                lines.add(current.toString().stripTrailing());
                current.setLength(0);
            }
            current.append(part);
        }
        if (!current.isEmpty()) {
            lines.add(current.toString().stripTrailing());
        }
    }
}
