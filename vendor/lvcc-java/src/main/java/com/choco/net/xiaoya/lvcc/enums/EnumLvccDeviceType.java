package com.choco.net.xiaoya.lvcc.enums;

/**
 * @Author: tanning
 * @Date: 2024/06/27/14:25
 * @Description: 设备类型
 */
public enum EnumLvccDeviceType {
    ONE(1,"老款10路充电桩(IG1000、IG1001)"),
    TWO(2,"老款10路充电柜(BG1000)"),
    THREE(3,"投币改造模块(UG0000)"),
    FOUR(4,"老款双插小功率(PG0200)"),
    FIVE(5,"老款单路大功率(PH0100)"),
    SEVEN(7,"新款10路充电桩(IG1031、IG1022、IG1050)"),
    EIGHT(8,"新款10路充电桩-注塑-液晶(OG1020)"),
    NINE(9,"新款双插大功率(PH0200、OH0200)"),
    TEN(10,"新款单插大功率(PH0110)"),

    ELEVEN(11,"门禁设备(DOOR)"),
    TWELVE(12,"新款20路充电桩(IG2020)"),
    THIRTEEN(13,"无线433网关"),
    FOURTEEN(14,"433设备2路大功率(PH0200-433)"),
    FIFTEEN(15,"双路门禁道闸"),
    SIXTEEN(16,"Air202双插(PH0210、OH0200、OH0210)"),
    SEVENTEEN(17,"Air720双插(PH0220、OH0220)"),
    EIGHTEEN(18,"单机版20路(IG2021)"),
    NINETEEN(19,"单机版10路(IG1032)"),

    TWENTY(20,"汽车7KW交流桩(OH0100)"),
    TWENTY_ONE(21,"直流6路充电桩(OH0601-D)"),
    TWENTY_TWO(22,"8+2充电柜"),
    TWENTY_THREE(23,"8路充电柜BG0811"),
    TWENTY_FOUR(24,"户外20无广告位，无刷卡板"),
    TWENTY_FIVE(25,"新投币改造升级模块-2G版"),
    TWENTY_SIX(26,"M10_BL0939"),
    TWENTY_EIGHT(28,"WIFI双插PH0240"),
    TWENTY_NINE(29,"Air720双插(OH0230)"),

    THIRTY(30,"8BC-1充电柜"),
    THIRTY_TWO(32,"直流4路充电桩"),
    THIRTY_FOUR(34,"Air720-IG1080(10BL-6)"),
    THIRTY_FIVE(35,"汽车7KW交流桩(AA700130)"),
    THIRTY_SIX(36,"换电柜BG0830"),
    THIRTY_SEVEN(37,"汽车14KW交流桩（AA140210)"),
    THIRTY_EIGHT(38,"8路充电柜BG0810"),
    THIRTY_NINE(39,"10路广告机(IG1060、10BL-4)"),

    FORTY(40,"20路广告机(IG2030、20BL-2)"),
    FORTY_TOW(42,"IG1052-复旦微"),
    FORTY_THREE(43,"复旦微IG1052-单机"),
    FORTY_FOUR(44,"OG1020-FM"),
    FORTY_FIVE(45,"485双插主机"),
    FORTY_SIX(46,"485双插从机"),
    FORTY_SEVEN(47,"10BL-1"),
    FORTY_EIGHT(48,"汽车7KW交流桩(AA700140)"),
    FORTY_NINE(49,"1ZA7-2(汽车7KW交流桩)"),

    FIFTY(50,"2ZA7-1/2(汽车桩14KW)"),
    FIFTY_ONE(51,"1ZA7-2-OFFLINE"),
    FIFTY_TWO(52,"2ZA7-1-OFFLINE"),
    FIFTY_THREE(53,"汽车7KW交流桩(1ZA7-1)"),
    FIFTY_FOUR(54,"OG1020-FM-OFFLINE"),
    FIFTY_FIVE(55,"汽车11KW交流桩(1ZA11-1)"),
    FIFTY_SIX(56,"汽车22KW交流桩(1ZA22-1)"),
    FIFTY_SEVEN(57,"20BL-1"),
    FIFTY_EIGHT(58,"MB02_4G门禁"),
    FIFTY_NINE(59,"RS485组网双插-主机-复旦微方案"),

    SIXTY(60,"RS485组网双插-从机-复旦微方案"),
    SIXTY_ONE(61,"BG1010-FM 8+2充电柜"),
    SIXTY_THREE(63,"4路充电柜4BC-1-FM"),
    SIXTY_FOUR(64,"10路广告机(10BL-3)"),
    SIXTY_SIX(66,"10路(10BL-5-FM)"),
    SIXTY_SEVEN(67,"1OA7_1_OFFLINE"),
    SIXTY_EIGHT(68,"2路(OH0230_EC600N)"),
    SIXTY_NINE(69,"双插大功率(OH0250-WIFI)"),

    SEVENTY(70,"OH0100-OFFLINE"),
    SEVENTY_ONE(71,"1ZA7_1-OFFLINE"),
    SEVENTY_TWO(72,"AA700130-OFFLINE"),
    SEVENTY_THREE(73,"AA1400210-OFFLINE"),
    SEVENTY_FOUR(74,"1ZA11_1_OFFLINE"),
    SEVENTY_FIVE(75,"换电柜8BH-2"),
    SEVENTY_SIX(76,"汽车交流42KW-JN"),
    SEVENTY_SEVEN(77,"1ZA22_1_OFFLINE"),
    SEVENTY_EIGHT(78,"汽车交流1OA11_1"),
    SEVENTY_NINE(79,"汽车交流1OA7_1"),

    EIGHTY(80,"充电柜(10BC-1)"),
    EIGHTY_ONE(81,"汽车交流1OA22_1"),
    EIGHTY_TWO(82,"汽车交流1OA11-1-OFFLINE"),
    EIGHTY_THREE(83,"汽车交流1OA22-1-OFFLINE"),
    EIGHTY_FOUR(84,"换电柜12BH-1"),
    EIGHTY_FIVE(85,"12路充电桩12BL-1"),
    EIGHTY_SIX(86,"12BC-1充电柜"),
    EIGHTY_SEVEN(87,"1MA7_2_OFFLINE"),
    EIGHTY_EIGHT(88,"1MA7_2"),
    EIGHTY_NINE(89,"10BL-1（10BL-7）MP3"),

    NINETY(90,"OH0260-ye"),
    NINETY_ONE(91,"OH0260-ye-Slave"),
    NINETY_TWO(92,"2ZA3-1"),
    NINETY_THREE(93,"10BL-8"),
    NINETY_FOUR(94,"2ZA3-1-OFFLINE"),
    NINETY_FIVE(95,"10BL-5-Basics"),
    NINETY_SIX(96,"20BL-1-MP3"),
    NINETY_SEVEN(97,"2BL-1");



    private int code;
    private String name;

    EnumLvccDeviceType(int code, String name) {
        this.code=code;
        this.name=name;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public static String getNameByCode(int code){
        for (EnumLvccDeviceType deviceType : EnumLvccDeviceType.values()) {
           if(deviceType.getCode()==code){
               return deviceType.getName();
           }
        }
        return "";
    }



}
