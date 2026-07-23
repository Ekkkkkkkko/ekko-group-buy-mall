package cn.ekko.types.dynamic.config.center.domain.model.valobj;

/**
 * 动态配置调整值对象。
 */
public class AttributeVO {

    private String attribute;

    private String value;

    public AttributeVO() {
    }

    public AttributeVO(String attribute, String value) {
        this.attribute = attribute;
        this.value = value;
    }

    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

}
