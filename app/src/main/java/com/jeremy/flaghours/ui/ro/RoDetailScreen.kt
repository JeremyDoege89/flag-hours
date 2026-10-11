package com.jeremy.flaghours.ui.ro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeremy.flaghours.data.db.Job
import com.jeremy.flaghours.data.db.RepairOrder
import com.jeremy.flaghours.data.remote.LookupResult
import com.jeremy.flaghours.data.remote.OlpService
import com.jeremy.flaghours.data.remote.VehicleLoad
import com.jeremy.flaghours.data.remote.VehicleQuery
import com.jeremy.flaghours.domain.EngineMatcher
import com.jeremy.flaghours.domain.JobMatcher
import com.jeremy.flaghours.domain.LaborRow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import com.jeremy.flaghours.domain.JobStatus
import com.jeremy.flaghours.domain.PayCalculator
import com.jeremy.flaghours.domain.PayPlan
import com.jeremy.flaghours.ui.common.ChoiceChips
import com.jeremy.flaghours.ui.common.ConfirmDialog
import com.jeremy.flaghours.ui.common.ScreenScaffold
import com.jeremy.flaghours.ui.common.app
import com.jeremy.flaghours.ui.common.onMain
import com.jeremy.flaghours.ui.common.fmtDate
import com.jeremy.flaghours.ui.common.hrs
import kotlinx.coroutines.launch

@Composable
fun RoDetailScreen(roId: Long, onBack: () -> Unit, onEditRo: (Long) -> Unit) {
    val application = app()
    val repo = application.repository
    val vehicles = application.vehicles
    val apiKey by application.secrets.olpApiKey.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf<String?>(null) }
    val olp = application.olp
    val quota by olp.quota.collectAsState()
    var refState by remember { mutableStateOf<VehicleLoad?>(null) }
    var refLoading by remember { mutableStateOf(false) }
    var browseFor by remember { mutableStateOf<Job?>(null) }
    var browseAll by remember { mutableStateOf(false) }
    var manualPick by remember { mutableStateOf(false) }
    var engineChoice by remember { mutableStateOf<VehicleLoad.ChooseEngine?>(null) }
    val autoTried = remember { mutableSetOf<Long>() }
    val scope = rememberCoroutineScope()
    val ro by remember(roId) { repo.observeRo(roId) }.collectAsState(initial = null)
    val jobs by remember(roId) { repo.observeJobsForRo(roId) }.collectAsState(initial = emptyList())
    val allJobs by remember { repo.jobs }.collectAsState(initial = emptyList())
    val plan by remember { repo.plan }.collectAsState(initial = PayPlan())
    var editing by remember { mutableStateOf<Job?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val r = ro ?: run {
        ScreenScaffold(title = "RO", onBack = onBack) {}
        return
    }
    val byId = allJobs.associate { it.job.id to it.toLine() }

    val query = r.year?.let { y ->
        if (r.make.isNullOrBlank() || r.model.isNullOrBlank()) null
        else VehicleQuery(y, r.make, r.model, r.engine?.let { EngineMatcher.displacement(it) }, r.engine?.let { EngineMatcher.cylinders(it) })
    }
    LaunchedEffect(query) { refState = query?.let { olp.cached(it) } }
    val readyRows = (refState as? VehicleLoad.Ready)?.rows

    fun handle(result: VehicleLoad) {
        when (result) {
            is VehicleLoad.ChooseEngine -> { engineChoice = result; refState = null }
            else -> refState = result
        }
    }
    fun loadRefs() {
        val q = query ?: return
        refLoading = true
        scope.launch {
            handle(olp.load(q))
            refLoading = false
        }
    }
    fun pick(job: Job, row: LaborRow) {
        scope.launch { repo.applyReference(job.id, row) }
    }

    // Auto-match lines without a reference: remembered picks first, then clear-cut matches.
    LaunchedEffect(readyRows, jobs) {
        val rows = readyRows ?: return@LaunchedEffect
        for (j in jobs) {
            if (j.refJob != null || j.id in autoTried) continue
            autoTried += j.id
            val slug = repo.rememberedRefSlug(j.description, r.make, r.model)
            val row = slug?.let { s -> rows.firstOrNull { it.jobSlug == s } }
                ?: JobMatcher.match(j.description, rows).takeIf { JobMatcher.isConfident(it) }?.first()?.row
            if (row != null) repo.applyReference(j.id, row, remember = slug == null)
        }
    }

    fun decodeVin(vin: String) {
        busy = "vin"
        scope.launch {
            when (val res = vehicles.decodeVin(vin)) {
                is LookupResult.Found -> {
                    val v = res.value
                    repo.saveRo(r.copy(year = v.year ?: r.year, make = v.make ?: r.make, model = v.model ?: r.model, engine = v.engine ?: r.engine))
                    snackbar.showSnackbar("Vehicle filled in from the VIN")
                }
                LookupResult.NotFound -> snackbar.showSnackbar("NHTSA couldn't decode that VIN. Check it against the RO.")
                is LookupResult.Failed -> snackbar.showSnackbar(res.message)
            }
            busy = null
        }
    }

    ScreenScaffold(
        title = "RO ${r.roNumber}",
        onBack = onBack,
        snackbar = snackbar,
        actions = {
            IconButton(onClick = { onEditRo(r.id) }) { Icon(Icons.Default.Edit, "Edit RO") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete RO") }
        },
        fab = {
            ExtendedFloatingActionButton(
                onClick = { editing = Job(roId = r.id, description = "", flaggedHundredths = 0, workDate = repo.today()) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add line") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                RoHeader(r, plan) {
                    val vin = r.vin
                    if (vin != null && vin.length == 17) {
                        TextButton(onClick = { decodeVin(vin) }, enabled = busy != "vin") {
                            Text(if (busy == "vin") "Decoding VIN…" else "Fill vehicle from VIN (NHTSA)")
                        }
                    }
                    LaborRefPanel(
                        hasKey = !apiKey.isNullOrBlank(),
                        vehicleLabel = query?.label,
                        state = refState,
                        loading = refLoading,
                        quotaRemaining = quota.remaining,
                        onLoad = ::loadRefs,
                        onBrowse = { browseAll = true },
                        onManual = { manualPick = true },
                        onChangeEngine = {
                            val st = refState as? VehicleLoad.Ready ?: return@LaborRefPanel
                            engineChoice = VehicleLoad.ChooseEngine(st.vehicleKey, st.engines)
                        },
                    )
                }
            }
            if (jobs.isEmpty()) item { Text("No labor lines yet. Tap Add line.") }
            items(jobs, key = { it.id }) { job ->
                JobCard(
                    job = job,
                    share = PayCalculator.myShare(job.toLine(r.roNumber), plan, byId),
                    plan = plan,
                    onClick = { editing = job },
                    onStatus = { s -> scope.launch { repo.setJobStatus(job.id, s) } },
                    badge = {
                        JobReference(
                            job, plan.decimals,
                            suggestions = readyRows?.let { rows -> JobMatcher.match(job.description, rows) },
                            onPick = { row -> pick(job, row) },
                            onMore = { browseFor = job },
                        )
                    },
                )
            }
            if (readyRows != null || jobs.any { it.refJob != null || it.refLowHundredths != null }) {
                item { Text(OlpService.ATTRIBUTION, style = MaterialTheme.typography.bodySmall) }
            }
            item {
                val total = jobs.sumOf { PayCalculator.myShare(it.toLine(r.roNumber), plan, byId) }
                Text("My total on this RO: ${hrs(total, plan.decimals)} h", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    editing?.let { j ->
        JobEditorDialog(
            initial = j,
            ro = r,
            plan = plan,
            allJobs = allJobs,
            onSave = { updated -> scope.launch { repo.saveJob(updated, r); editing = null } },
            onDelete = if (j.id != 0L) ({ scope.launch { repo.deleteJob(j); editing = null } }) else null,
            onDismiss = { editing = null },
        )
    }
    browseFor?.let { j ->
        readyRows?.let { rows ->
            JobBrowserSheet(rows, j.description, plan.decimals, "Typical time for \"${j.description}\"", onPick = { row -> pick(j, row); browseFor = null }, onDismiss = { browseFor = null })
        }
    }
    if (browseAll) {
        readyRows?.let { rows -> JobBrowserSheet(rows, "", plan.decimals, "All jobs for ${query?.label ?: "this vehicle"}", onPick = null, onDismiss = { browseAll = false }) }
    }
    (refState as? VehicleLoad.ChooseMake)?.let { c ->
        OptionDialog("Which make?", "Not sure which catalog make matches (${c.tried}).", c.options.map { it.slug to it.name }, onPick = { slug ->
            val q = query ?: return@OptionDialog
            refState = null; refLoading = true
            scope.launch { handle(olp.loadForMake(q, slug)); refLoading = false }
        }, onDismiss = { refState = null })
    }
    (refState as? VehicleLoad.ChooseModel)?.let { c ->
        OptionDialog("Which model?", "Pick the catalog model for ${query?.label} (tried ${c.tried}).", c.options.map { it.slug to it.name }, onPick = { slug ->
            val q = query ?: return@OptionDialog
            refState = null; refLoading = true
            scope.launch { handle(olp.loadVehicle(q, c.makeSlug, slug)); refLoading = false }
        }, onDismiss = { refState = null })
    }
    engineChoice?.let { c ->
        OptionDialog("Which engine?", "This vehicle has several engines in the catalog.", c.options.map { it to it } + ("" to "Not sure (show all engines)"), onPick = { e ->
            val q = query ?: return@OptionDialog
            engineChoice = null
            scope.launch { refState = olp.chooseEngine(q, c.vehicleKey, e) }
        }, onDismiss = { engineChoice = null })
    }
    if (manualPick) {
        ManualVehicleDialog(olp, r.year, onPicked = { year, make, model ->
            manualPick = false
            refLoading = true
            scope.launch {
                val updated = r.copy(year = year, make = r.make ?: make.name, model = r.model ?: model.name)
                if (updated != r) repo.saveRo(updated)
                val q = VehicleQuery(year, updated.make!!, updated.model!!, r.engine?.let { EngineMatcher.displacement(it) }, r.engine?.let { EngineMatcher.cylinders(it) })
                olp.forget(q)
                handle(olp.loadVehicle(q, make.slug, model.slug))
                refLoading = false
            }
        }, onDismiss = { manualPick = false })
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete RO ${r.roNumber}?",
            "This deletes the RO and all ${jobs.size} of its lines. This can't be undone.",
            "Delete",
            onConfirm = { scope.launch { repo.deleteRo(r); onMain(onBack) } },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun RoHeader(r: RepairOrder, plan: PayPlan, extra: @Composable () -> Unit = {}) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("RO ${r.roNumber} · ${fmtDate(r.openedDate)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (r.vehicleLabel.isNotBlank()) Text(r.vehicleLabel + (r.engine?.let { " · $it" } ?: ""))
            r.vin?.let { Text("VIN $it", style = MaterialTheme.typography.bodyMedium) }
            r.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            r.photoUri?.let { RoPhoto(it) }
            extra()
        }
    }
}

@Composable
fun JobCard(job: Job, share: Int, plan: PayPlan, onClick: () -> Unit, onStatus: (JobStatus) -> Unit, badge: @Composable () -> Unit = {}) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(job.description, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${hrs(share, plan.decimals)} h", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            val details = buildList {
                add(job.laborType.label)
                add("Flagged ${hrs(job.flaggedHundredths, plan.decimals)}")
                job.adjustedHundredths?.let { add("Adjusted ${hrs(it, plan.decimals)} (${hrs(job.flaggedHundredths - it, plan.decimals)} cut)") }
                if (job.mySplitPct != 100) add("${job.mySplitPct}% split")
                if (job.comebackOfJobId != null) add("Comeback")
                add("Worked ${fmtDate(job.workDate)}")
            }
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            badge()
            ChoiceChips(JobStatus.entries, job.status, { it.label }, onStatus)
        }
    }
}
