package com.example.clock.ui.model

import com.example.clock.R

enum class MenuEnum(val title: String, val icon: Int) {
    QRCODE("扫码", R.drawable.outline_qr_code_scanner_24),
    BOOKMARK("书签", R.drawable.outline_star_outline_24),
    FIND("查找", R.drawable.outline_search_24),
    HISTORY("历史", R.drawable.outline_history_24),
    VIEW_SOURCE("源代码", R.drawable.baseline_code_24),

    VIEW_CURRENT_SOURCE("此刻源码", R.drawable.outline_html_24),
    AD_PICK("拾取广告", R.drawable.outline_deselect_24),
    LOAD_RESOURCE("网页资源", R.drawable.outline_file_present_24),
    CLEAR_CACHE("清除数据", R.drawable.outline_cleaning_services_24),
    SETTINGS("设置", R.drawable.outline_settings_24),

    RESTORE_TAB("恢复Tab", R.drawable.outline_undo_24),
    PC_MODE("PC模式", R.drawable.outline_computer_24),
    READ_MODE("阅读模式", R.drawable.outline_menu_book_24),
    SITE_SETTING("本站设置", R.drawable.outline_video_settings_24),
    EXIT("退出App", R.drawable.outline_exit_to_app_24),

    FULL_SCREEN("全屏", R.drawable.outline_fullscreen_24),
    SCRIPT("脚本", R.drawable.outline_javascript_24)
}