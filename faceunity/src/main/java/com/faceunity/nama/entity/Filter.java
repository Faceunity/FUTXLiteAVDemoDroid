package com.faceunity.nama.entity;

/**
 * 美颜滤镜
 *
 * @author Richie on 2020.02.23
 */
public class Filter {
    private String name;
    private int iconId;
    private String desc;

    public Filter(String name, int iconId, String desc) {
        this.name = name;
        this.iconId = iconId;
        this.desc = desc;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getIconId() {
        return iconId;
    }

    public void setIconId(int iconId) {
        this.iconId = iconId;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    @Override
    public String toString() {
        return "Filter{" +
                "name='" + name + '\'' +
                ", desc='" + desc + '\'' +
                '}';
    }
}