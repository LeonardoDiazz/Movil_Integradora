package com.sgr.app.ui.admin

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.widget.*
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sgr.app.R
import com.sgr.app.databinding.FragmentEquipmentBinding
import com.sgr.app.utils.showConfirmDialog
import com.sgr.app.model.CreateEquipmentRequest
import com.sgr.app.model.Equipment
import com.sgr.app.model.Reservation
import com.sgr.app.model.Space
import com.sgr.app.model.UpdateEquipmentRequest
import com.sgr.app.network.RetrofitClient
import kotlinx.coroutines.launch

class EquipmentFragment : Fragment() {

    private var _binding: FragmentEquipmentBinding? = null
    private val binding get() = _binding!!

    private var currentPage = 0
    private var totalPages = 1

    private val condicionLabels = arrayOf("Todos", "Disponible", "En uso", "Mantenimiento")
    private val condicionValues = arrayOf("", "DISPONIBLE", "EN_USO", "MANTENIMIENTO")

    private val categoriaLabels = arrayOf("Todos", "Audiovisual", "Cómputo", "Laboratorio")
    private val categoriaValues = arrayOf("", "AUDIOVISUAL", "COMPUTO", "LABORATORIO")

    private val accesoLabels = arrayOf("Todos", "Permite alumnos", "Restringido")
    private val accesoValues = arrayOf("", "ALUMNOS", "RESTRINGIDO")

    private var filterCondicion = ""
    private var filterCategoria = ""
    private var filterAcceso = ""
    private var isInitializing = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEquipmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().title = ""
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        setupSpinner(binding.spinnerCondicion, condicionLabels) { pos ->
            if (!isInitializing) {
                filterCondicion = condicionValues[pos]; filterCategoria = ""; filterAcceso = ""; currentPage = 0; load()
            }
        }
        setupSpinner(binding.spinnerCategoria, categoriaLabels) { pos ->
            if (!isInitializing) {
                filterCategoria = categoriaValues[pos]; filterCondicion = ""; filterAcceso = ""; currentPage = 0; load()
            }
        }
        setupSpinner(binding.spinnerAcceso, accesoLabels) { pos ->
            if (!isInitializing) {
                filterAcceso = accesoValues[pos]; filterCondicion = ""; filterCategoria = ""; currentPage = 0; load()
            }
        }

        isInitializing = false

        binding.btnClearFilters.setOnClickListener {
            filterCondicion = ""; filterCategoria = ""; filterAcceso = ""
            currentPage = 0
            isInitializing = true
            binding.spinnerCondicion.setSelection(0)
            binding.spinnerCategoria.setSelection(0)
            binding.spinnerAcceso.setSelection(0)
            isInitializing = false
            load()
        }



        binding.btnPrev.setOnClickListener { if (currentPage > 0) { currentPage--; load() } }
        binding.btnNext.setOnClickListener { if (currentPage < totalPages - 1) { currentPage++; load() } }

        load()
    }

    private fun setupSpinner(spinner: Spinner, labels: Array<String>, onSelect: (Int) -> Unit) {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { onSelect(pos) }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun load() {
        val backendFilter = when {
            filterCondicion.isNotEmpty() -> filterCondicion
            filterCategoria.isNotEmpty() -> filterCategoria
            filterAcceso.isNotEmpty() -> filterAcceso
            else -> ""
        }
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())
                val response = api.getEquipments(currentPage, 4, backendFilter, "")
                if (response.isSuccessful) {
                    val page = response.body() ?: return@launch
                    totalPages = page.totalPages.coerceAtLeast(1)
                    binding.tvPage.text = "Pág ${currentPage + 1} / $totalPages"
                    binding.recyclerView.adapter = EquipmentAdapter(
                        items = page.content,
                        onView = { showViewEquipmentDialog(it) },
                        onEdit = { showEditEquipmentDialog(it) },
                        onHistory = { showEquipmentHistory(it) },
                        onToggle = { eq ->
                            lifecycleScope.launch {
                                try { RetrofitClient.create(requireContext()).toggleEquipmentStatus(eq.id); load() }
                                catch (_: Exception) {}
                            }
                        }
                    )
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Error al cargar equipos", Toast.LENGTH_SHORT).show()
            } finally { binding.progressBar.visibility = View.GONE }
        }
    }

    private fun buildEquipmentForm(eq: Equipment? = null): Pair<ScrollView, () -> CreateEquipmentRequest?> {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 16, 48, 0) }

        fun label(text: String) = TextView(ctx).apply { this.text = text; textSize = 12f; setPadding(0, 12, 0, 2) }

        val etInvNumber = EditText(ctx).apply { hint = "Número de inventario"; setText(eq?.inventoryNumber ?: "") }
        val etName = EditText(ctx).apply { hint = "Nombre del equipo"; setText(eq?.name ?: "") }
        val etDescription = EditText(ctx).apply { hint = "Descripción"; minLines = 2; setText(eq?.description ?: "") }

        val catLabels = arrayOf("Audiovisual", "Cómputo", "Laboratorio")
        val catValues = arrayOf("AUDIOVISUAL", "COMPUTO", "LABORATORIO")
        val spinnerCat = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, catLabels)
            val idx = catValues.indexOf(eq?.category ?: "COMPUTO").coerceAtLeast(0)
            setSelection(idx)
        }

        val condLabels = arrayOf("Disponible", "En uso", "Mantenimiento")
        val condValues = arrayOf("DISPONIBLE", "EN_USO", "MANTENIMIENTO")
        val spinnerCond = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, condLabels)
            val idx = condValues.indexOf(eq?.equipmentCondition ?: "DISPONIBLE").coerceAtLeast(0)
            setSelection(idx)
        }

        val estadoLabels = arrayOf("Activo", "Inactivo")
        val spinnerEstado = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, estadoLabels)
            setSelection(if (eq?.active != false) 0 else 1)
        }

        val cbStudents = CheckBox(ctx).apply { text = "Permitir acceso a estudiantes"; isChecked = eq?.allowStudents ?: true }

        layout.addView(label("Número de inventario *")); layout.addView(etInvNumber)
        layout.addView(label("Nombre del Equipo *")); layout.addView(etName)
        layout.addView(label("Tipo")); layout.addView(spinnerCat)
        layout.addView(label("Descripción")); layout.addView(etDescription)
        layout.addView(label("Condición")); layout.addView(spinnerCond)
        layout.addView(label("Estado")); layout.addView(spinnerEstado)
        layout.addView(cbStudents)

        val sv = ScrollView(ctx).apply { addView(layout) }

        val builder: () -> CreateEquipmentRequest? = {
            val inv = etInvNumber.text.toString().trim()
            val name = etName.text.toString().trim()
            if (inv.isEmpty() || name.isEmpty()) {
                Toast.makeText(ctx, "Número de inventario y nombre son requeridos", Toast.LENGTH_SHORT).show(); null
            } else CreateEquipmentRequest(
                inventoryNumber = inv,
                name = name,
                category = catValues[spinnerCat.selectedItemPosition],
                description = etDescription.text.toString().trim(),
                allowStudents = cbStudents.isChecked,
                equipmentCondition = condValues[spinnerCond.selectedItemPosition],
                active = spinnerEstado.selectedItemPosition == 0
            )
        }
        return Pair(sv, builder)
    }

    private fun showCreateEquipmentDialog() {
        val (view, buildRequest) = buildEquipmentForm()
        AlertDialog.Builder(requireContext())
            .setTitle("Agregar equipo")
            .setView(view)
            .setPositiveButton("Crear") { _, _ ->
                val req = buildRequest() ?: return@setPositiveButton
                lifecycleScope.launch {
                    try {
                        val resp = RetrofitClient.create(requireContext()).createEquipment(req)
                        if (resp.isSuccessful) {
                            Toast.makeText(requireContext(), "Equipo creado", Toast.LENGTH_SHORT).show(); load()
                        } else Toast.makeText(requireContext(), "Error: ${resp.code()}", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) { Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show() }
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun showEditEquipmentDialog(eqPreview: Equipment) {
        val ctx = requireContext()
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_equipment_edit, null)

        val etInvNumber   = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditInvNumber)
        val etName        = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditName)
        val etDescription = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditDescription)
        val actvCategory  = view.findViewById<AutoCompleteTextView>(R.id.actvEditCategory)
        val actvSpace     = view.findViewById<AutoCompleteTextView>(R.id.actvEditSpace)
        val cbStudents    = view.findViewById<android.widget.CheckBox>(R.id.cbEditAllowStudents)

        // Listas de tipo/categoría
        val catLabels = arrayOf("Cómputo", "Audiovisual", "Laboratorio")
        val catValues = arrayOf("COMPUTO", "AUDIOVISUAL", "LABORATORIO")
        actvCategory.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, catLabels))

        // Pre-llenar con datos del listado
        etInvNumber.setText(eqPreview.inventoryNumber)
        etName.setText(eqPreview.name)
        etDescription.setText(eqPreview.description)
        cbStudents.isChecked = eqPreview.allowStudents ?: true
        val catIdx = catValues.indexOfFirst { it == eqPreview.category }.coerceAtLeast(0)
        actvCategory.setText(catLabels[catIdx], false)

        // Placeholder mientras cargan espacios
        actvSpace.setText("Cargando...", false)

        val dialog = AlertDialog.Builder(ctx)
            .setView(view)
            .create()
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        view.findViewById<TextView>(R.id.btnEditEquipClose).setOnClickListener  { dialog.dismiss() }
        view.findViewById<TextView>(R.id.btnEditEquipCancel).setOnClickListener { dialog.dismiss() }

        // Lista de espacios y datos completos del equipo
        var spacesList: List<Space> = emptyList()
        var currentCondition = eqPreview.equipmentCondition ?: "DISPONIBLE"
        var selectedSpaceId: Long? = eqPreview.spaceId

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(ctx)

                // Cargar datos completos del equipo (para spaceId y condition)
                val eqResp = api.getEquipment(eqPreview.id)
                val fullEq = if (eqResp.isSuccessful) eqResp.body() ?: eqPreview else eqPreview
                currentCondition = fullEq.equipmentCondition ?: "DISPONIBLE"
                selectedSpaceId  = fullEq.spaceId
                etInvNumber.setText(fullEq.inventoryNumber)
                etName.setText(fullEq.name)
                etDescription.setText(fullEq.description)
                cbStudents.isChecked = fullEq.allowStudents ?: true
                val idx = catValues.indexOfFirst { it == fullEq.category }.coerceAtLeast(0)
                actvCategory.setText(catLabels[idx], false)

                // Cargar espacios para el dropdown
                val spacesResp = api.getSpaces(0, 100, "", "")
                if (spacesResp.isSuccessful) {
                    spacesList = spacesResp.body()?.content ?: emptyList()
                    val spaceLabels = mutableListOf("Sin espacio asociado")
                    spaceLabels.addAll(spacesList.map { it.name })
                    actvSpace.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, spaceLabels))

                    val currentSpace = spacesList.find { it.id == fullEq.spaceId }
                    actvSpace.setText(currentSpace?.name ?: "Sin espacio asociado", false)
                }
            } catch (_: Exception) {
                actvSpace.setText("Sin espacio asociado", false)
            }
        }

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnEditEquipSave)
            .setOnClickListener {
                val inv  = etInvNumber.text.toString().trim()
                val name = etName.text.toString().trim()
                val desc = etDescription.text.toString().trim()

                if (inv.isEmpty() || inv.length < 3) {
                    Toast.makeText(ctx, "El número de inventario debe tener al menos 3 caracteres", Toast.LENGTH_SHORT).show(); return@setOnClickListener
                }
                if (name.isEmpty() || name.length < 3) {
                    Toast.makeText(ctx, "El nombre debe tener al menos 3 caracteres", Toast.LENGTH_SHORT).show(); return@setOnClickListener
                }
                if (desc.length < 10) {
                    Toast.makeText(ctx, "La descripción debe tener al menos 10 caracteres", Toast.LENGTH_SHORT).show(); return@setOnClickListener
                }

                val selectedCatLabel = actvCategory.text.toString()
                val selectedCat = catValues[catLabels.indexOfFirst { it == selectedCatLabel }.coerceAtLeast(0)]

                val selectedSpaceName = actvSpace.text.toString()
                val spaceId = spacesList.find { it.name == selectedSpaceName }?.id

                showConfirmDialog(ctx, "¿Guardar cambios?", "Se actualizarán los datos del equipo. ¿Deseas continuar?") {
                        lifecycleScope.launch {
                            try {
                                val req = UpdateEquipmentRequest(
                                    inventoryNumber = inv,
                                    name            = name,
                                    category        = selectedCat,
                                    description     = desc,
                                    allowStudents   = cbStudents.isChecked,
                                    condition       = currentCondition,
                                    active          = eqPreview.active ?: true,
                                    spaceId         = spaceId
                                )
                                val resp = RetrofitClient.create(ctx).updateEquipment(eqPreview.id, req)
                                if (resp.isSuccessful) {
                                    Toast.makeText(ctx, "Equipo actualizado", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss(); load()
                                } else Toast.makeText(ctx, "Error: ${resp.code()}", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {
                                Toast.makeText(ctx, "Error de conexión", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
            }

        dialog.show()
    }

    private fun showViewEquipmentDialog(eq: Equipment) {
        val ctx = requireContext()
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_equipment_detail, null)

        fun fillDialog(e: Equipment) {
            view.findViewById<TextView>(R.id.tvDetailInvNumber).text  = e.inventoryNumber.ifBlank { "—" }
            view.findViewById<TextView>(R.id.tvDetailName).text       = e.name.ifBlank { "—" }
            view.findViewById<TextView>(R.id.tvDetailCategory).text   = e.category.ifBlank { "—" }
            view.findViewById<TextView>(R.id.tvDetailSpace).text      = e.spaceName?.ifBlank { "—" } ?: "—"
            view.findViewById<TextView>(R.id.tvDetailStatus).text     = if (e.active == true) "Activo" else "Inactivo"
            view.findViewById<TextView>(R.id.tvDetailCondition).text  = when (e.equipmentCondition) {
                "DISPONIBLE"    -> "Disponible"
                "EN_USO"        -> "En uso"
                "MANTENIMIENTO" -> "Mantenimiento"
                else            -> e.equipmentCondition?.ifBlank { "—" } ?: "—"
            }
            view.findViewById<TextView>(R.id.tvDetailStudents).text   = if (e.allowStudents == true) "Permitido" else "Restringido"
            view.findViewById<TextView>(R.id.tvDetailDescription).text = e.description.ifBlank { "—" }
        }

        fillDialog(eq)

        val dialog = AlertDialog.Builder(ctx)
            .setView(view)
            .create()

        view.findViewById<TextView>(R.id.btnEquipDetailClose).setOnClickListener { dialog.dismiss() }
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnEquipDetailDismiss)
            .setOnClickListener { dialog.dismiss() }

        dialog.show()

        // Cargar datos completos desde el API (incluye spaceName)
        lifecycleScope.launch {
            try {
                val resp = RetrofitClient.create(ctx).getEquipment(eq.id)
                if (resp.isSuccessful && dialog.isShowing) {
                    resp.body()?.let { fillDialog(it) }
                }
            } catch (_: Exception) { }
        }
    }

    private fun showEquipmentHistory(eq: Equipment) {
        val ctx = requireContext()
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_equipment_history, null)
        view.findViewById<TextView>(R.id.tvHistoryEquipName).text = eq.name
        val layoutEmpty = view.findViewById<View>(R.id.layoutEmpty)
        val rvHistory   = view.findViewById<RecyclerView>(R.id.rvHistory)
        layoutEmpty.visibility = View.VISIBLE
        rvHistory.visibility   = View.GONE

        val dialog = AlertDialog.Builder(ctx)
            .setView(view)
            .create()

        view.findViewById<TextView>(R.id.btnHistoryClose).setOnClickListener { dialog.dismiss() }
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnHistoryDismiss)
            .setOnClickListener { dialog.dismiss() }

        dialog.show()
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        lifecycleScope.launch {
            try {
                val resp = RetrofitClient.create(ctx).getEquipmentHistory(eq.id)
                if (resp.isSuccessful) {
                    val items = resp.body() ?: emptyList()
                    if (items.isEmpty()) {
                        layoutEmpty.visibility = View.VISIBLE
                        rvHistory.visibility   = View.GONE
                    } else {
                        layoutEmpty.visibility = View.GONE
                        rvHistory.visibility   = View.VISIBLE
                        rvHistory.layoutManager = LinearLayoutManager(ctx)
                        rvHistory.adapter = EquipmentHistoryAdapter(items)
                    }
                } else {
                    layoutEmpty.visibility = View.VISIBLE
                    rvHistory.visibility   = View.GONE
                }
            } catch (_: Exception) {
                if (dialog.isShowing) {
                    layoutEmpty.visibility = View.VISIBLE
                    rvHistory.visibility   = View.GONE
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class EquipmentAdapter(
    private val items: List<Equipment>,
    private val onView: (Equipment) -> Unit,
    private val onEdit: (Equipment) -> Unit,
    private val onHistory: (Equipment) -> Unit,
    private val onToggle: (Equipment) -> Unit
) : RecyclerView.Adapter<EquipmentAdapter.VH>() {

    inner class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_equipment, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        holder.view.apply {
            findViewById<TextView>(R.id.tvInvNumber).text = e.inventoryNumber
            findViewById<TextView>(R.id.tvEquipName).text = e.name
            findViewById<TextView>(R.id.tvCategory).text = e.category.ifBlank { "—" }
            findViewById<TextView>(R.id.tvAccess).text = if (e.allowStudents == true) "Alumnos" else "Restringido"
            findViewById<TextView>(R.id.tvDescription).text = e.description.ifBlank { "—" }

            // Badge ESTADO
            val badgeEstado = findViewById<TextView>(R.id.tvBadgeEstado)
            if (e.active == true) {
                badgeEstado.text = "Activo"
                badgeEstado.setBackgroundResource(R.drawable.bg_badge_green)
                badgeEstado.setTextColor(0xFF065F46.toInt())
            } else {
                badgeEstado.text = "Inactivo"
                badgeEstado.setBackgroundResource(R.drawable.bg_badge_red)
                badgeEstado.setTextColor(0xFF991B1B.toInt())
            }

            // Badge CONDICIÓN
            val badgeCond = findViewById<TextView>(R.id.tvBadgeCond)
            when (e.equipmentCondition ?: "") {
                "DISPONIBLE" -> {
                    badgeCond.text = "Disponible"
                    badgeCond.setBackgroundResource(R.drawable.bg_badge_green)
                    badgeCond.setTextColor(0xFF065F46.toInt())
                }
                "EN_USO" -> {
                    badgeCond.text = "En uso"
                    badgeCond.setBackgroundResource(R.drawable.bg_badge_yellow)
                    badgeCond.setTextColor(0xFF92400E.toInt())
                }
                else -> {
                    badgeCond.text = "Mantenimiento"
                    badgeCond.setBackgroundResource(R.drawable.bg_badge_red)
                    badgeCond.setTextColor(0xFF991B1B.toInt())
                }
            }

            findViewById<ImageButton>(R.id.btnView).setOnClickListener { onView(e) }
            findViewById<ImageButton>(R.id.btnEdit).setOnClickListener { onEdit(e) }
            findViewById<ImageButton>(R.id.btnHistory).setOnClickListener { onHistory(e) }
            findViewById<ImageButton>(R.id.btnToggle).setOnClickListener { onToggle(e) }
        }
    }
}

class EquipmentHistoryAdapter(
    private val items: List<Reservation>
) : RecyclerView.Adapter<EquipmentHistoryAdapter.VH>() {

    inner class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.view.apply {
            // Solicitante
            findViewById<TextView>(R.id.tvHistoryBy).text   = r.requesterName ?: "—"
            // Fecha
            findViewById<TextView>(R.id.tvHistoryDate).text = r.reservationDate ?: "—"
            // Horario
            val schedule = r.schedule ?: if (r.startTime != null) "${r.startTime} – ${r.endTime ?: ""}" else "—"
            findViewById<TextView>(R.id.tvHistoryDetails).text = schedule

            // Estado badge con color
            val tvStatus = findViewById<TextView>(R.id.tvHistoryAction)
            when (r.status) {
                "PENDIENTE" -> {
                    tvStatus.text = "Pendiente"
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_yellow)
                    tvStatus.setTextColor(0xFF92400E.toInt())
                }
                "APROBADA" -> {
                    tvStatus.text = "Aprobada"
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_green)
                    tvStatus.setTextColor(0xFF065F46.toInt())
                }
                "RECHAZADA" -> {
                    tvStatus.text = "Rechazada"
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_red)
                    tvStatus.setTextColor(0xFF991B1B.toInt())
                }
                "DEVUELTA" -> {
                    tvStatus.text = "Devuelta"
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_blue)
                    tvStatus.setTextColor(0xFF1D4ED8.toInt())
                }
                else -> {
                    tvStatus.text = "Cancelada"
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_gray)
                    tvStatus.setTextColor(0xFF374151.toInt())
                }
            }
        }
    }
}
