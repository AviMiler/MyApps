@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.myappstore.smsforwarder.ui.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myappstore.smsforwarder.Graph
import com.myappstore.smsforwarder.R
import com.myappstore.smsforwarder.contacts.ContactNumber
import com.myappstore.smsforwarder.contacts.PhoneContact
import com.myappstore.smsforwarder.contacts.RecentSender
import com.myappstore.smsforwarder.core.Phones
import com.myappstore.smsforwarder.data.Party
import com.myappstore.smsforwarder.engine.Permissions
import com.myappstore.smsforwarder.ui.Fmt
import com.myappstore.smsforwarder.ui.components.Avatar
import com.myappstore.smsforwarder.ui.components.CheckCircle
import com.myappstore.smsforwarder.ui.components.EmptyState
import com.myappstore.smsforwarder.ui.components.InfoCard
import com.myappstore.smsforwarder.ui.components.LoadingBox
import com.myappstore.smsforwarder.ui.components.LocalResumeTick
import com.myappstore.smsforwarder.ui.components.PartyChip
import com.myappstore.smsforwarder.ui.components.PermissionAsk
import com.myappstore.smsforwarder.ui.components.PrimaryButton
import com.myappstore.smsforwarder.ui.components.Segmented
import com.myappstore.smsforwarder.ui.components.TopBar
import com.myappstore.smsforwarder.ui.components.fieldColors
import com.myappstore.smsforwarder.ui.components.rememberNow
import com.myappstore.smsforwarder.ui.theme.Halaa

/**
 * Full-screen picker for senders or recipients: phone contacts, recent SMS senders
 * (handy for banks and services that send under a name) or a typed number / id.
 */
@Composable
fun PartyPicker(
    title: String,
    initial: List<Party>,
    onDone: (List<Party>) -> Unit,
    onDismiss: () -> Unit,
    showRecent: Boolean = true,
) {
    val context = LocalContext.current
    val colors = Halaa.colors
    val selected = remember { mutableStateListOf<Party>().apply { addAll(initial) } }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }

    val tick = LocalResumeTick.current
    var contactsGranted by remember { mutableStateOf(Permissions.canReadContacts(context)) }
    var smsGranted by remember { mutableStateOf(Permissions.canReadSms(context)) }
    LaunchedEffect(tick) {
        contactsGranted = Permissions.canReadContacts(context)
        smsGranted = Permissions.canReadSms(context)
    }
    val contacts by produceState<List<PhoneContact>?>(initialValue = null, contactsGranted) {
        value = if (contactsGranted) Graph.contacts.loadAll() else emptyList()
    }
    val recent by produceState<List<RecentSender>?>(initialValue = null, smsGranted) {
        value = if (smsGranted) Graph.contacts.recentSenders() else emptyList()
    }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        contactsGranted = it
        if (it) Graph.contacts.invalidate()
    }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        smsGranted = it
    }

    fun isSelected(address: String) = selected.any { Phones.same(it.address, address) }
    fun toggle(party: Party) {
        val index = selected.indexOfFirst { Phones.same(it.address, party.address) }
        if (index >= 0) selected.removeAt(index) else selected.add(party)
    }
    fun addParty(party: Party) {
        if (party.address.isNotBlank() && !isSelected(party.address)) selected.add(party)
    }

    BackHandler(onBack = onDismiss)

    val contactsLabel = stringResource(R.string.picker_tab_contacts)
    val recentLabel = stringResource(R.string.picker_tab_recent)
    val manualLabel = stringResource(R.string.picker_tab_manual)
    val tabs = listOfNotNull(0 to contactsLabel, if (showRecent) 1 to recentLabel else null, 2 to manualLabel)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding(),
    ) {
        TopBar(title = title, onBack = onDismiss) {
            PrimaryButton(
                text = if (selected.isEmpty()) {
                    stringResource(R.string.picker_done)
                } else {
                    stringResource(R.string.picker_done_count, selected.size)
                },
                onClick = { onDone(selected.toList()) },
                height = 44.dp,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        if (tab != 2) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.picker_search), color = colors.inkFaint) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.inkSoft) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = null, tint = colors.inkSoft)
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = fieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(10.dp))
        }
        Segmented(
            options = tabs,
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (selected.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(selected.toList(), key = { Phones.key(it.address) }) { party ->
                    PartyChip(party, onRemove = { toggle(party) })
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> ContactsTab(
                    contacts = contacts,
                    granted = contactsGranted,
                    query = query,
                    isSelected = ::isSelected,
                    onToggle = ::toggle,
                    onGrant = { contactsPermission.launch(Permissions.CONTACTS) },
                )
                1 -> RecentTab(
                    recent = recent,
                    granted = smsGranted,
                    query = query,
                    isSelected = ::isSelected,
                    onToggle = ::toggle,
                    onGrant = { smsPermission.launch(Permissions.READ_SMS) },
                )
                else -> ManualTab(onAdd = ::addParty)
            }
        }
    }
}

private fun initialOf(name: String): String {
    val first = name.trim().firstOrNull() ?: return "#"
    return if (first.isLetter()) first.uppercase() else "#"
}

private val bottomSpace = 32.dp

@Composable
private fun ContactsTab(
    contacts: List<PhoneContact>?,
    granted: Boolean,
    query: String,
    isSelected: (String) -> Boolean,
    onToggle: (Party) -> Unit,
    onGrant: () -> Unit,
) {
    if (!granted) {
        PermissionAsk(
            icon = Icons.Rounded.Contacts,
            title = stringResource(R.string.perm_contacts_title),
            text = stringResource(R.string.perm_contacts_text),
            onGrant = onGrant,
        )
        return
    }
    if (contacts == null) {
        LoadingBox()
        return
    }
    val filtered = remember(contacts, query) {
        val q = query.trim()
        contacts.filter { contact ->
            q.isEmpty() || contact.name.contains(q, ignoreCase = true) ||
                contact.numbers.any { Phones.matchesQuery(it.address, q) }
        }
    }
    if (filtered.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.picker_no_results),
            body = stringResource(R.string.picker_no_results_body),
        )
        return
    }
    val starred = if (query.isBlank()) filtered.filter { it.starred } else emptyList()
    val groups = remember(filtered) { filtered.groupBy { initialOf(it.name) } }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(contentPadding = PaddingValues(bottom = bottomSpace + navBottom)) {
        if (starred.isNotEmpty()) {
            stickyHeader(key = "header-starred") {
                LetterHeader(stringResource(R.string.picker_favorites), starredHeader = true)
            }
            items(starred, key = { "starred-${it.id}" }) { contact ->
                ContactRow(contact, isSelected, onToggle)
            }
        }
        groups.forEach { (letter, list) ->
            stickyHeader(key = "header-$letter") { LetterHeader(letter) }
            items(list, key = { "contact-${it.id}" }) { contact ->
                ContactRow(contact, isSelected, onToggle)
            }
        }
    }
}

@Composable
private fun LetterHeader(text: String, starredHeader: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Halaa.colors.background)
            .padding(horizontal = 22.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (starredHeader) {
            Icon(Icons.Rounded.Star, contentDescription = null, tint = Halaa.colors.warning, modifier = Modifier.padding(end = 6.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall, color = Halaa.colors.brand)
    }
}

@Composable
private fun ContactRow(contact: PhoneContact, isSelected: (String) -> Boolean, onToggle: (Party) -> Unit) {
    val anySelected = contact.numbers.any { isSelected(it.address) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle(contact.party(contact.numbers.first())) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(contact.name, contact.photoUri, 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                contact.name,
                style = MaterialTheme.typography.titleSmall,
                color = Halaa.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (contact.numbers.size == 1) {
                Text(
                    numberLine(contact.numbers.first()),
                    style = MaterialTheme.typography.bodySmall,
                    color = Halaa.colors.inkSoft,
                    maxLines = 1,
                )
            } else {
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    contact.numbers.forEach { number ->
                        NumberChip(number, isSelected(number.address)) { onToggle(contact.party(number)) }
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        CheckCircle(anySelected)
    }
}

private fun numberLine(number: ContactNumber): String =
    listOf(number.label, Phones.pretty(number.address)).filter { it.isNotBlank() }.joinToString(" · ")

@Composable
private fun NumberChip(number: ContactNumber, selected: Boolean, onClick: () -> Unit) {
    val colors = Halaa.colors
    Text(
        numberLine(number),
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) colors.onBrand else colors.inkSoft,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) colors.brand else colors.sunken)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun RecentTab(
    recent: List<RecentSender>?,
    granted: Boolean,
    query: String,
    isSelected: (String) -> Boolean,
    onToggle: (Party) -> Unit,
    onGrant: () -> Unit,
) {
    if (!granted) {
        PermissionAsk(
            icon = Icons.Rounded.Sms,
            title = stringResource(R.string.perm_read_sms_title),
            text = stringResource(R.string.perm_read_sms_text),
            onGrant = onGrant,
        )
        return
    }
    if (recent == null) {
        LoadingBox()
        return
    }
    val q = query.trim()
    val filtered = recent.filter { sender ->
        q.isEmpty() || sender.name.orEmpty().contains(q, ignoreCase = true) || Phones.matchesQuery(sender.address, q)
    }
    if (filtered.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.picker_no_recent),
            body = stringResource(R.string.picker_no_recent_body),
        )
        return
    }
    val context = LocalContext.current
    val now = rememberNow()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(contentPadding = PaddingValues(bottom = bottomSpace + navBottom)) {
        item {
            Text(
                stringResource(R.string.picker_recent_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Halaa.colors.inkSoft,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            )
        }
        items(filtered, key = { it.address }) { sender ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(sender.party()) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(sender.name ?: sender.address, sender.photoUri, 46.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        sender.name ?: Phones.pretty(sender.address),
                        style = MaterialTheme.typography.titleSmall,
                        color = Halaa.colors.ink,
                        maxLines = 1,
                    )
                    Text(
                        sender.lastBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = Halaa.colors.inkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        Fmt.ago(context, sender.lastAt, now),
                        style = MaterialTheme.typography.labelSmall,
                        color = Halaa.colors.inkFaint,
                    )
                    Spacer(Modifier.height(4.dp))
                    CheckCircle(isSelected(sender.address))
                }
            }
        }
    }
}

@Composable
private fun ManualTab(onAdd: (Party) -> Unit) {
    val colors = Halaa.colors
    var address by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(stringResource(R.string.manual_title), style = MaterialTheme.typography.titleMedium, color = colors.ink)
        Text(stringResource(R.string.manual_desc), style = MaterialTheme.typography.bodySmall, color = colors.inkSoft)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text(stringResource(R.string.manual_address)) },
            placeholder = { Text("050-1234567 / Leumi", color = colors.inkFaint) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.manual_name)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = stringResource(R.string.manual_add),
            icon = Icons.Rounded.Add,
            enabled = address.isNotBlank(),
            onClick = {
                onAdd(Party(address = address.trim(), name = name.trim().ifBlank { null }))
                address = ""
                name = ""
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        InfoCard(
            title = stringResource(R.string.manual_tip_title),
            text = stringResource(R.string.manual_tip),
            icon = Icons.Rounded.Lightbulb,
            color = colors.held,
        )
    }
}
