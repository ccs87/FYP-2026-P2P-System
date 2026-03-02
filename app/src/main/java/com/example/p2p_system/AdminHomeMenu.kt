package com.example.p2p_system

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun AdminHomeMenu(
    username: String,
    onReturnToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!Database.isAdmin(username)) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Access denied")
        }
        return
    }

    val allRecords by Database.recordsFlow.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var refreshTick by remember { mutableIntStateOf(0) }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    val users = remember(refreshTick) { Database.getAllUserSnapshots() }

    fun toggleUserStatus(target: String) {
        val current = Database.getStatus(target)
        val next = if (current == UserStatus.ACTIVE) UserStatus.DEACTIVATED else UserStatus.ACTIVE
        Database.setStatus(target, next)
        refreshTick++
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Admin Management",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onReturnToLogin) { Text("Return to Login") }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Logged in as: $username",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(8.dp))

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("User Account Status") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Transaction Log") }
                )
            }

            Spacer(Modifier.height(12.dp))

            when (selectedTab) {
                0 -> {
                    Text("Users", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(users) { u ->
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = u.username + if (u.isAdmin) " (admin)" else "",
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(u.status.name) }
                                        )
                                    }

                                    Spacer(Modifier.height(6.dp))

                                    Text("Balance: \$${"%.2f".format(u.balance)}")

                                    Spacer(Modifier.height(10.dp))

                                    val canManage = !u.isAdmin
                                    Button(
                                        onClick = { toggleUserStatus(u.username) },
                                        enabled = canManage
                                    ) {
                                        Text(
                                            if (u.status == UserStatus.ACTIVE) "Deactivate" else "Activate"
                                        )
                                    }

                                    if (!canManage) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            "Admin account cannot be deactivated here.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    Text("Transaction Log", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))

                    if (allRecords.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No transactions found")
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(allRecords) { rec ->
                                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(
                                            text = "${rec.fromUser} \u2192 ${rec.toUser}",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text("Amount: \$${"%.0f".format(rec.amount)}")
                                        Text("Time: ${sdf.format(rec.timestamp)}")
                                        rec.note?.let {
                                            Spacer(Modifier.height(2.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        }

        Text(
            text = "© CCS87-CS4514, 2025-2026",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}
