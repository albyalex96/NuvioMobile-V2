package com.nuvio.app.features.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.livetv.LiveTvSport
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.features.livetv.LiveTvSportsRemindersRepository
import com.nuvio.app.features.livetv.LiveTvSportsSettingsRepository
import com.nuvio.app.features.livetv.liveTvSportsLiveAlertIntervals
import com.nuvio.app.features.livetv.liveTvSportsReminderLeadChoices
import com.nuvio.app.features.livetv.liveTvSportsLeagues
import nuvio.composeapp.generated.resources.*
import nuvio.composeapp.generated.resources.settings_sports_all_description
import nuvio.composeapp.generated.resources.settings_sports_all_title
import nuvio.composeapp.generated.resources.settings_sports_leagues_count
import nuvio.composeapp.generated.resources.settings_sports_reset_description
import nuvio.composeapp.generated.resources.settings_sports_reset_title
import nuvio.composeapp.generated.resources.settings_sports_section_general
import nuvio.composeapp.generated.resources.settings_sports_section_leagues
import nuvio.composeapp.generated.resources.settings_sports_sport_american_football
import nuvio.composeapp.generated.resources.settings_sports_sport_aussie_rules
import nuvio.composeapp.generated.resources.settings_sports_sport_baseball
import nuvio.composeapp.generated.resources.settings_sports_sport_basketball
import nuvio.composeapp.generated.resources.settings_sports_sport_cricket
import nuvio.composeapp.generated.resources.settings_sports_sport_fighting
import nuvio.composeapp.generated.resources.settings_sports_sport_golf
import nuvio.composeapp.generated.resources.settings_sports_sport_hockey
import nuvio.composeapp.generated.resources.settings_sports_sport_lacrosse
import nuvio.composeapp.generated.resources.settings_sports_sport_other
import nuvio.composeapp.generated.resources.settings_sports_sport_racing
import nuvio.composeapp.generated.resources.settings_sports_sport_rugby
import nuvio.composeapp.generated.resources.settings_sports_sport_soccer
import nuvio.composeapp.generated.resources.settings_sports_sport_tennis
import nuvio.composeapp.generated.resources.settings_sports_sport_volleyball
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Settings → Sports: which leagues the Live TV Sports section shows, grouped by sport. */
internal fun LazyListScope.sportsSettingsContent(isTablet: Boolean) {
    item(key = "sports_settings") {
        SportsSettingsSection(isTablet = isTablet)
    }
}

internal val LiveTvSport.labelRes: StringResource
    get() = when (this) {
        LiveTvSport.Soccer -> Res.string.settings_sports_sport_soccer
        LiveTvSport.Basketball -> Res.string.settings_sports_sport_basketball
        LiveTvSport.AmericanFootball -> Res.string.settings_sports_sport_american_football
        LiveTvSport.Hockey -> Res.string.settings_sports_sport_hockey
        LiveTvSport.Baseball -> Res.string.settings_sports_sport_baseball
        LiveTvSport.Rugby -> Res.string.settings_sports_sport_rugby
        LiveTvSport.Cricket -> Res.string.settings_sports_sport_cricket
        LiveTvSport.AussieRules -> Res.string.settings_sports_sport_aussie_rules
        LiveTvSport.Fighting -> Res.string.settings_sports_sport_fighting
        LiveTvSport.Racing -> Res.string.settings_sports_sport_racing
        LiveTvSport.Golf -> Res.string.settings_sports_sport_golf
        LiveTvSport.Tennis -> Res.string.settings_sports_sport_tennis
        LiveTvSport.Volleyball -> Res.string.settings_sports_sport_volleyball
        LiveTvSport.Lacrosse -> Res.string.settings_sports_sport_lacrosse
        LiveTvSport.Other -> Res.string.settings_sports_sport_other
    }

@Composable
private fun SportsSettingsSection(isTablet: Boolean) {
    val settings by remember {
        LiveTvSportsSettingsRepository.ensureLoaded()
        LiveTvSportsSettingsRepository.settings
    }.collectAsStateWithLifecycle()
    val bySport = remember { liveTvSportsLeagues.groupBy { it.sport } }
    // Expanded sports, saved as names so the choice survives configuration changes.
    var expanded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val tokens = MaterialTheme.nuvio
    val nestedPadding = if (isTablet) 40.dp else 32.dp

    Column {
        SettingsSection(
            title = stringResource(Res.string.settings_sports_section_general),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_sports_reset_title),
                    description = stringResource(Res.string.settings_sports_reset_description),
                    icon = Icons.Rounded.RestartAlt,
                    enabled = settings.isCustom,
                    isTablet = isTablet,
                    onClick = LiveTvSportsSettingsRepository::resetToDefaults,
                )
            }
        }

        if (LiveTvSportsRemindersRepository.isSupported) {
            SportsRemindersSection(isTablet = isTablet)
        }

        SettingsSection(
            title = stringResource(Res.string.settings_sports_section_leagues),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                LiveTvSport.entries.filter { it in bySport }.forEachIndexed { index, sport ->
                    val leagues = bySport.getValue(sport)
                    val active = leagues.count(settings::isEnabled)
                    val isExpanded = sport.name in expanded
                    if (index > 0) SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(sport.labelRes),
                        description = stringResource(Res.string.settings_sports_leagues_count, active, leagues.size),
                        icon = sport.icon,
                        isTablet = isTablet,
                        trailingContent = {
                            Icon(
                                imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                tint = tokens.colors.textMuted,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        onClick = {
                            expanded = if (isExpanded) expanded - sport.name else expanded + sport.name
                        },
                    )
                    AnimatedVisibility(visible = isExpanded) {
                        Column {
                            SettingsGroupDivider(isTablet = isTablet)
                            SettingsSwitchRow(
                                title = stringResource(Res.string.settings_sports_all_title),
                                description = stringResource(Res.string.settings_sports_all_description),
                                checked = active == leagues.size,
                                isTablet = isTablet,
                                horizontalPadding = nestedPadding,
                                onCheckedChange = { LiveTvSportsSettingsRepository.setSportEnabled(sport, it) },
                            )
                            leagues.forEach { league ->
                                SettingsGroupDivider(isTablet = isTablet)
                                SettingsSwitchRow(
                                    title = league.label,
                                    checked = settings.isEnabled(league),
                                    isTablet = isTablet,
                                    horizontalPadding = nestedPadding,
                                    onCheckedChange = { LiveTvSportsSettingsRepository.setLeagueEnabled(league, it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SportsRemindersSection(isTablet: Boolean) {
    val options by remember {
        LiveTvSportsRemindersRepository.ensureLoaded()
        LiveTvSportsRemindersRepository.options
    }.collectAsStateWithLifecycle()
    val testTitle = stringResource(Res.string.live_tv_sports_notification_test_title)
    val testBody = stringResource(Res.string.live_tv_sports_notification_test_body)

    SettingsSection(
        title = stringResource(Res.string.settings_sports_section_reminders),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_sports_reminders_title),
                description = stringResource(Res.string.settings_sports_reminders_description),
                checked = options.enabled,
                isTablet = isTablet,
                onCheckedChange = LiveTvSportsRemindersRepository::setEnabled,
            )
            if (options.enabled) {
                SettingsGroupDivider(isTablet = isTablet)
                SettingsChipRow(
                    title = stringResource(Res.string.settings_sports_reminder_lead_title),
                    description = stringResource(Res.string.settings_sports_reminder_lead_description),
                    isTablet = isTablet,
                ) {
                    liveTvSportsReminderLeadChoices.forEach { minutes ->
                        Chip(
                            label = if (minutes == 0) {
                                stringResource(Res.string.settings_sports_reminder_lead_kickoff)
                            } else {
                                stringResource(Res.string.settings_sports_reminder_lead_minutes, minutes)
                            },
                            selected = minutes in options.leadMinutes,
                            onClick = { LiveTvSportsRemindersRepository.toggleLeadMinutes(minutes) },
                        )
                    }
                }
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_sports_live_alerts_title),
                    description = stringResource(Res.string.settings_sports_live_alerts_description),
                    checked = options.liveAlertsEnabled,
                    isTablet = isTablet,
                    onCheckedChange = LiveTvSportsRemindersRepository::setLiveAlertsEnabled,
                )
                if (options.liveAlertsEnabled) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsChipRow(
                        title = stringResource(Res.string.settings_sports_live_interval_title),
                        description = stringResource(Res.string.settings_sports_live_interval_description),
                        isTablet = isTablet,
                    ) {
                        liveTvSportsLiveAlertIntervals.forEach { minutes ->
                            Chip(
                                label = stringResource(Res.string.settings_sports_live_interval_minutes, minutes),
                                selected = options.liveAlertIntervalMinutes == minutes,
                                onClick = { LiveTvSportsRemindersRepository.setLiveAlertInterval(minutes) },
                            )
                        }
                    }
                }
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_sports_reminder_test_title),
                    description = stringResource(Res.string.settings_sports_reminder_test_description),
                    icon = Icons.Rounded.NotificationsActive,
                    isTablet = isTablet,
                    onClick = { LiveTvSportsRemindersRepository.sendTestNotification(testTitle, testBody) },
                )
            }
        }
    }
}
