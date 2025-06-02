package de.zrb.bund.newApi.sentence;

public class SentenceField {

    private String name;
    private Integer position;
    private Integer length;
    private Integer row;
    private String valuePattern; // z. B. "[0-9]{8}" oder "[A-Z]{3}[0-9]{5}"
    private String color;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public Integer getLength() {
        return length;
    }

    public void setLength(Integer length) {
        this.length = length;
    }

    public Integer getRow() {
        return row;
    }

    public void setRow(Integer row) {
        this.row = row;
    }

    public String getValuePattern() {
        return valuePattern;
    }

    public void setValuePattern(String valuePattern) {
        this.valuePattern = valuePattern;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}