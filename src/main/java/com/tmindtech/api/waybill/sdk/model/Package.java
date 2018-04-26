package com.tmindtech.api.waybill.sdk.model;

import java.util.List;

public class Package {

    public Integer seqNo; // 包裹的序号，例如 1,2,3

    public Number weight; // 重量（单位kg，保留2位小数）

    public Number volumeLong; // 包裹总长（单位cm，保留2位小数）

    public Number volumeWidth; // 包裹总宽（单位cm，保留2位小数）

    public Number volumeHeight; // 包裹总宽（单位cm，保留2位小数）

    public Number volume; // 体积（单位cm3，保留2位小数）

    public List<Cargo> cargoList; // 包裹列表
}
