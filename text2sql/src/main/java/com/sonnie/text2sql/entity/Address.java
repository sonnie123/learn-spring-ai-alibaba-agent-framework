package com.sonnie.text2sql.entity;

public record Address(
        String name,        // 收件人姓名
        String phone,       // 联系电话
        String province,    // 省
        String city,        // 市
        String district,    // 区/县
        String detail       // 详细地址
) {}