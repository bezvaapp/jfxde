package dev.jfxde.fxmisc.richtext;

import java.util.List;

import org.fxmisc.richtext.CodeArea;

import dev.jfxde.jx.tools.Lexer;
import dev.jfxde.jx.tools.Token;

public class HighlightWrapper extends GenericStyledAreaWrapper<CodeArea> {

    private Token token;
    private Lexer lexer;
    private int areaLength;

    public HighlightWrapper(CodeArea area, Lexer lexer) {
        super(area);
        this.lexer = lexer;
        this.areaLength = area.getLength();
    }

    void highlightDelimiters(int caretPosition) {

        if (area.getLength() != areaLength) {
            areaLength = area.getLength();

            return;
        }

        if (token != null) {
            removeHighlightDelimiter();
        }

        token = lexer.getToken(caretPosition).stream().filter(Token::isDelimiter).findFirst().orElse(null);
        if (token != null) {
            highlightDelimiter();
        }
    }

    private void highlightDelimiter() {
        if (token.isDelimiter()) {
// Don't do this because the CodeArea merges the styles of adjacent segments and thus changes color of all the adjacent delimiters.
//            token.getStyle().add("block-delimiter-match");
//            token.getOppositeToken().getStyle().add("block-delimiter-match");
            area.setStyle(token.getStart(), token.getEnd(), List.of("block-delimiter-match"));
            area.setStyle(token.getOppositeToken().getStart(), token.getOppositeToken().getEnd(), List.of("block-delimiter-match"));
        }
    }

    private void removeHighlightDelimiter() {
        if (token.isDelimiter()) {
            token.resetStyle();
            token.getOppositeToken().resetStyle();
            area.setStyle(token.getStart(), token.getEnd(), token.getStyle());
            area.setStyle(token.getOppositeToken().getStart(), token.getOppositeToken().getEnd(), token.getOppositeToken().getStyle());
        }
    }

    void setAreaLength(int areaLength) {
        this.areaLength = areaLength;
    }

    void setToken(Token token) {

        if (token != null && token.isDelimiter()) {

            token.getStyle().clear();
            token.getStyle().add("block-delimiter-match");
            token.getOppositeToken().getStyle().clear();
            token.getOppositeToken().getStyle().add("block-delimiter-match");
            this.token = token;
        }
    }
}
