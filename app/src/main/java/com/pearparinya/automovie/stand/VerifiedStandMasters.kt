package com.pearparinya.automovie.stand

object VerifiedStandMasters {
    val records = listOf(
        StandMasterRecord("ST-001", "97048624e1df12c0cd0550c0c3d57cb61460f9b5d351b2099ee60c2f6dee0121"), // 1000037173.png
        StandMasterRecord("ST-002", "27d35be64f386c20f1995d0f4ff01028f269938a9c543d66053a292323a23897"), // 1000037175.png
        StandMasterRecord("ST-003", "85e6ce7c90e089a17303f48877de5471bb19633ed21d871c859dbcc5aa9e7dd5"), // 1000037178.png
        StandMasterRecord("ST-004", "7ccd2f733dbf71423701f7293b05e19d3c68ae78a37b0b7120ffa596c57a7b77"), // 1000037180.png
        StandMasterRecord("ST-005", "c4a9605df44e557d1ab17a18eea36c098b4fe844e1399540569922bf992b5ff2"), // 1000037182.png
        StandMasterRecord("ST-006", "86ebf75b12452dbe3f8e048553c1c909b24432c927554fc983163dc497315291"), // 1000037195.png
        StandMasterRecord("ST-007", "56801da9664e39d2a28f0e27a377b8138942dfdb216d65b32343c2e39f7f89af"), // 1000037194.png
        StandMasterRecord("ST-008", "2530200e252e1fa94f8a4143089e43adf67e44d00daeef7161f63da6b9662aa3"), // 1000037200.png
        StandMasterRecord("ST-009", "030ff45cd8fa466dec756a6acb30317ada0cd86044ba9a892e13a53546ddb7de"), // 1000037199.png
        StandMasterRecord("ST-010", "363e298d805561dfbe79669a00af220912e02be4200d7b476303028766f00fe5"), // 1000037198.png
        StandMasterRecord("ST-011", "863c8e75f6139935d096b67de6667a5ae7534f999fc98ad1d48c244c28a4adee"), // 1000037197.png
        StandMasterRecord("ST-012", "8c0a693529cd88a492a1861ef44851d631344682cfb0af41879320ea72fc8218"), // 1000037196.png
        StandMasterRecord("ST-013", "6c46e64179b7c417d2311677a35575a8409e757175a3eb69989c63052d3ff50a"), // 1000037270.png
        StandMasterRecord("ST-014", "56cb7e1f1ce5494d6c8df29dc23ad644b44107b00b2c6fdb9a3bcd9ea65dad4e"), // 1000037202.png
        StandMasterRecord("ST-015", "17a8daade4ad348d799cef21fb6a9c96b20cd549e1850d6edacb9c0e19c919c2"), // 1000037203.png
        StandMasterRecord("ST-016", "6c81a219e6851f0a6e36c0ee50859deb2c11f6b5f2ffd103a120b3b91e79f863") // 1000037204.png
    )
    init { check(StandMasterIntegrity.complete(records)) }
}
