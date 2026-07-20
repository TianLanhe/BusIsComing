package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyRouteTerminologyContractTest {
    private val traditional = values(File("src/main/res/values"))
    private val simplified = values(File("src/main/res/values-b+zh+Hans"))
    private val english = values(File("src/main/res/values-en"))

    @Test
    fun savedOriginDestinationConfigurationsUseJourneyTerminology() {
        assertCopy(
            traditional,
            mapOf(
                "first_run_add_route" to "新增常用行程",
                "manage_routes" to "管理行程",
                "settings_group_route_data" to "行程資料",
                "settings_route_transfer" to "匯入與匯出常用行程",
                "route_info" to "行程資訊",
                "route_name_hint" to "行程名稱",
                "route_manage_title" to "行程管理",
                "add_route" to "新增行程",
                "frequent_routes_label" to "常用行程",
                "route_title_add" to "新增行程",
                "route_title_clone" to "複製行程",
                "route_title_edit" to "編輯行程",
                "select_route_first" to "請先選擇常用行程",
                "frequent_route_name_hint" to "常用行程名稱",
                "route_preview" to "行程預覽",
                "save_frequent_title" to "儲存為常用行程",
                "saved_as_frequent" to "已儲存為常用行程",
                "route_transfer_title" to "行程匯入與匯出",
                "route_transfer_export_all" to "匯出全部行程"
            )
        )
        assertCopy(
            simplified,
            mapOf(
                "first_run_add_route" to "添加常用行程",
                "manage_routes" to "管理行程",
                "settings_group_route_data" to "行程数据",
                "settings_route_transfer" to "导入与导出常用行程",
                "route_info" to "行程信息",
                "route_name_hint" to "行程名称",
                "route_manage_title" to "行程管理",
                "add_route" to "添加行程",
                "frequent_routes_label" to "常用行程",
                "route_title_add" to "添加行程",
                "route_title_clone" to "复制行程",
                "route_title_edit" to "编辑行程",
                "select_route_first" to "请先选择常用行程",
                "frequent_route_name_hint" to "常用行程名称",
                "route_preview" to "行程预览",
                "save_frequent_title" to "保存为常用行程",
                "saved_as_frequent" to "已保存为常用行程",
                "route_transfer_title" to "行程导入与导出",
                "route_transfer_export_all" to "导出全部行程"
            )
        )
        assertCopy(
            english,
            mapOf(
                "first_run_add_route" to "Add regular journey",
                "manage_routes" to "Manage journeys",
                "settings_group_route_data" to "Journey data",
                "settings_route_transfer" to "Import or export regular journeys",
                "route_info" to "Journey details",
                "route_name_hint" to "Journey name",
                "route_manage_title" to "Manage journeys",
                "add_route" to "Add journey",
                "frequent_routes_label" to "Regular journeys",
                "route_title_add" to "Add journey",
                "route_title_clone" to "Copy journey",
                "route_title_edit" to "Edit journey",
                "select_route_first" to "Choose a regular journey first",
                "frequent_route_name_hint" to "Regular journey name",
                "route_preview" to "Journey preview",
                "save_frequent_title" to "Save as regular journey",
                "saved_as_frequent" to "Saved as a regular journey",
                "route_transfer_title" to "Import and export journeys",
                "route_transfer_export_all" to "Export all journeys"
            )
        )
    }

    @Test
    fun queriedBusOptionsKeepRouteTerminology() {
        assertCopy(
            traditional,
            mapOf(
                "search_routes" to "搜尋路線",
                "sort_route" to "路線",
                "route_results_summary" to "共 %1\$d 條路線，%2\$d 條直達",
                "route_detail_unavailable" to "路線詳情暫不可用",
                "route_detail_loading" to "正在載入路線詳情",
                "monitor_route_unavailable" to "此路線暫時無法監控"
            )
        )
        assertCopy(
            simplified,
            mapOf(
                "search_routes" to "搜索路线",
                "sort_route" to "路线",
                "route_results_summary" to "共 %1\$d 条路线，%2\$d 条直达",
                "route_detail_unavailable" to "路线详情暂不可用",
                "route_detail_loading" to "正在加载路线详情",
                "monitor_route_unavailable" to "此路线暂时无法监控"
            )
        )
        assertCopy(
            english,
            mapOf(
                "search_routes" to "Find routes",
                "sort_route" to "Route",
                "route_results_summary" to "%1\$d routes, %2\$d direct",
                "route_detail_unavailable" to "Route details are unavailable",
                "route_detail_loading" to "Loading route details",
                "monitor_route_unavailable" to "This route cannot be monitored right now"
            )
        )
    }

    @Test
    fun bicroutesProtocolNameRemainsVisibleInEveryLocale() {
        listOf(traditional, simplified, english).forEach { localized ->
            assertTrue(localized.getValue("route_transfer_import_description").contains(".bicroutes"))
            assertTrue(localized.getValue("route_transfer_export_description").contains(".bicroutes"))
            assertTrue(localized.getValue("route_transfer_error_extension").contains(".bicroutes"))
        }
    }

    private fun assertCopy(actual: Map<String, String>, expected: Map<String, String>) {
        expected.forEach { (key, value) ->
            assertEquals("Unexpected copy for $key", value, actual.getValue(key))
        }
    }

    private fun values(directory: File): Map<String, String> =
        Regex(
            "<string\\s+name=\"([^\"]+)\"(?:\\s+[^>]*)?>(.*?)</string>",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
            .findAll(
                directory.listFiles()
                    .orEmpty()
                    .filter { it.isFile && it.extension == "xml" }
                    .joinToString("\n") { it.readText() }
            )
            .associate { it.groupValues[1] to it.groupValues[2] }
}
