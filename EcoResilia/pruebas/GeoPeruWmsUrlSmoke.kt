package com.ecoresilia.app.data

fun main() {
    val (x, y) = GeoPeruWmsUrl.tileXY(-16.409, -71.537, 8)
    check(x in 0..255 && y in 0..255)
    val bbox = GeoPeruWmsUrl.bbox3857(8, x, y).split(',').map(String::toDouble)
    check(bbox.size == 4 && bbox[0] < bbox[2] && bbox[1] < bbox[3])
    val url = GeoPeruWmsUrl.getMapUrl(
        "https://espacialg.geoperu.gob.pe/geoserver/geoperu/peru_inundacion_group_/wms",
        "geoperu:peru_inundacion_group_", 8, x, y)
    check("REQUEST=GetMap" in url && "TRANSPARENT=TRUE" in url)
    check("geoperu%3Aperu_inundacion_group_" in url)
    check("SRS=EPSG%3A3857" in url)
    println("OK: WMS GetMap URL; tile=$x,$y bbox=${bbox.joinToString()}")
}
