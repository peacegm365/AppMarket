package com.app.market.ui.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.market.resources.Res
import com.app.market.resources.theme
import com.app.market.resources.theme_summary
import com.app.market.resources.general
import com.app.market.resources.nav_search
import com.app.market.resources.nav_settings
import com.app.market.resources.nav_updates
import com.app.market.resources.settings_section_download_install
import com.app.market.resources.settings_section_download_install_summary
import com.app.market.resources.settings_section_general_summary
import com.app.market.resources.settings_section_search_summary
import com.app.market.resources.settings_section_sources
import com.app.market.resources.settings_section_sources_summary
import com.app.market.resources.settings_section_updates_summary
import com.app.market.ui.component.MainTabScaffold
import com.app.market.ui.component.PageVerticalPadding
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 设置一级菜单：各分类进入二级页（[SettingsCategoryScreen]）。
 */
@Composable
fun SettingsTab(
    bottomPadding: Dp,
    appManagementSupported: Boolean = true,
    onNavigateCategory: (String) -> Unit,
    onNavigateTheme: () -> Unit = {},
) {

    MainTabScaffold(title = stringResource(Res.string.nav_settings)) { topPadding, backdropModifier, scrollBehavior ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().then(backdropModifier).scrollEndHaptic().overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = topPadding + PageVerticalPadding,
                bottom = bottomPadding + PageVerticalPadding,
            ),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    ArrowPreference(
                        title = stringResource(Res.string.settings_section_sources),
                        summary = stringResource(Res.string.settings_section_sources_summary),
                        onClick = { onNavigateCategory(SettingsCategories.SOURCES) },
                    )
                    if (appManagementSupported) {
                        ArrowPreference(
                            title = stringResource(Res.string.nav_updates),
                            summary = stringResource(Res.string.settings_section_updates_summary),
                            onClick = { onNavigateCategory(SettingsCategories.UPDATES) },
                        )
                    }
                    // 「搜索」分类入口常显：精确搜索是全局设置，不随源能力变化而消失
                    run {
                        ArrowPreference(
                            title = stringResource(Res.string.nav_search),
                            summary = stringResource(Res.string.settings_section_search_summary),
                            onClick = { onNavigateCategory(SettingsCategories.SEARCH) },
                        )
                    }
                    ArrowPreference(
                        title = stringResource(Res.string.settings_section_download_install),
                        summary = stringResource(Res.string.settings_section_download_install_summary),
                        onClick = { onNavigateCategory(SettingsCategories.DOWNLOAD_INSTALL) },
                    )
                    ArrowPreference(
                        title = stringResource(Res.string.theme),
                        summary = stringResource(Res.string.theme_summary),
                        onClick = onNavigateTheme,
                    )
                    ArrowPreference(
                        title = stringResource(Res.string.general),
                        summary = stringResource(Res.string.settings_section_general_summary),
                        onClick = { onNavigateCategory(SettingsCategories.GENERAL) },
)
                }
            }
        }
    }
}