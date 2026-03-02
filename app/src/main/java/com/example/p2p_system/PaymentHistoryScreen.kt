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
fun PaymentHistoryScreen(
    username: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allRecords by Database.recordsFlow.collectAsState()
    var search by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    fun matchesQuery(rec: TransactionRecord, q: String): Boolean {
        if (q.isBlank()) return true
        val query = q.lowercase(Locale.getDefault())
        val otherParty = if (rec.fromUser == username) rec.toUser else rec.fromUser
        val typeText = if (rec.fromUser == username) "pay" else "receive"
        val dateText = sdf.format(rec.timestamp).lowercase(Locale.getDefault())
        val amountText = if (rec.amount % 1.0 == 0.0) "%.0f".format(rec.amount) else "%.2f".format(rec.amount)
        return listOf(
            otherParty.lowercase(Locale.getDefault()),
            typeText,
            amountText,
            dateText,
            rec.note?.lowercase(Locale.getDefault()) ?: ""
        ).any { it.contains(query) }
    }

    val filtered = remember(allRecords, username, search, selectedTab) {
        allRecords
            .asSequence()
            .filter { it.fromUser == username || it.toUser == username }
            .filter {
                when (selectedTab) {
                    1 -> it.fromUser == username
                    2 -> it.toUser == username
                    else -> true
                }
            }
            .filter { matchesQuery(it, search) }
            .sortedByDescending { it.timestamp }
            .toList()
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
                Text("Payment History", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("Back") }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search transactions") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("All") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Pay") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Receive payment") })
            }

            Spacer(Modifier.height(8.dp))

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No transactions found")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered) { rec ->
                        val isPay = rec.fromUser == username
                        val otherParty = if (isPay) rec.toUser else rec.fromUser
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    if (isPay) "Pay" else "Receive payment",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text("Party: $otherParty")
                                Text("Amount: \$${if (rec.amount % 1.0 == 0.0) "%.0f".format(rec.amount) else "%.2f".format(rec.amount)}")
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

        Text(
            text = "© CCS87-CS4514, 2025-2026",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}