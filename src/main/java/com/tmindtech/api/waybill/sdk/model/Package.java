package com.tmindtech.api.waybill.sdk.model;

import java.util.List;

public class Package {

    public Integer seqNo; // 包裹的序号，从0开始计数

    public Double weight; // 重量（单位kg，保留2位小数）

    public Double volumeLong; // 包裹总长（单位cm，保留2位小数）

    public Double volumeWidth; // 包裹总宽（单位cm，保留2位小数）

    public Double volume; // 体积（单位cm3，保留2位小数）

    public List<Cargo> cargoList; // 包裹列表
}
