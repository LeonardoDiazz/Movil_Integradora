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
import com.sgr.app.databinding.FragmentSpacesBinding
import com.sgr.app.utils.showConfirmDialog
import com.sgr.app.model.CreateSpaceRequest
import com.sgr.app.model.Reservation
import com.sgr.app.model.Space
import com.sgr.app.network.RetrofitClient
import kotlinx.coroutines.launch

class SpacesFragment : Fragment() {

    private var _binding: FragmentSpacesBinding? = null
    private val binding get() = _binding!!

    private var currentPage = 0
    private var totalPages = 1

    private val disponibilidadLabels = arrayOf("Todos", "Disponible", "Ocupado", "Mantenimiento")
    private val disponibilidadValues = arrayOf("", "DISPONIBLE", "OCUPADO", "MANTENIMIENTO")

    private val accesoLabels = arrayOf("Todos", "Permite alumnos", "Restringido")
    private val accesoValues = arrayOf("", "ALUMNOS", "RESTRINGIDO")

    private val capacidadLabels = arrayOf("Todos", "1 - 30", "31 - 100", "101+")
    private val capacidadValues = arrayOf("", "CAP_SMALL", "CAP_MEDIUM", "CAP_LARGE")

    private var filterDisponibilidad = ""
    private var filterAcceso = ""
    private var filterCapacidad = ""
    private var isInitializing = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSpacesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().title = ""
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        setupSpinner(binding.spinnerDisponibilidad, disponibilidadLabels) { pos ->
            if (!isInitializing) {
                filterDisponibilidad = disponibilidadValues[pos]
                filterAcceso = ""
                filterCapacidad = ""
                currentPage = 0
                load()
            }
        }
        setupSpinner(binding.spinnerAcceso, accesoLabels) { pos ->
            if (!isInitializing) {
                filterAcceso = accesoValues[pos]
                filterDisponibilidad = ""
                filterCapacidad = ""
                currentPage = 0
                load()
            }
        }
        setupSpinner(binding.spinnerCapacidad, capacidadLabels) { pos ->
            if (!isInitializing) {
                filterCapacidad = capacidadValues[pos]
                filterDisponibilidad = ""
                filterAcceso = ""
                currentPage = 0
                load()
            }
        }

        isInitializing = false

        binding.btnClearFilters.setOnClickListener {
            filterDisponibilidad = ""; filterAcceso = ""; filterCapacidad = ""
            currentPage = 0
            isInitializing = true
            binding.spinnerDisponibilidad.setSelection(0)
            binding.spinnerAcceso.setSelection(0)
            binding.spinnerCapacidad.setSelection(0)
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
            filterDisponibilidad.isNotEmpty() -> filterDisponibilidad
            filterCapacidad.isNotEmpty() -> filterCapacidad
            else -> ""
        }
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())
                val response = api.getSpaces(currentPage, 4, backendFilter, "")
                if (response.isSuccessful) {
                    val page = response.body() ?: return@launch
                    totalPages = page.totalPages.coerceAtLeast(1)
                    binding.tvPage.text = "Pág ${currentPage + 1} / $totalPages"
                    binding.recyclerView.adapter = SpaceAdapter(
                        items = page.content,
                        onView = { showViewSpaceDialog(it) },
                        onEdit = { showEditSpaceDialog(it) },
                        onHistory = { showSpaceHistory(it) },
                        onToggle = { space ->
                            lifecycleScope.launch {
                                try { RetrofitClient.create(requireContext()).toggleSpaceStatus(space.id); load() }
                                catch (_: Exception) {}
                            }
                        }
                    )
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Error al cargar espacios", Toast.LENGTH_SHORT).show()
            } finally { binding.progressBar.visibility = View.GONE }
        }
    }

    private fun buildSpaceForm(space: Space? = null): Pair<ScrollView, () -> CreateSpaceRequest?> {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 16, 48, 0) }

        fun label(text: String) = TextView(ctx).apply { this.text = text; textSize = 12f; setPadding(0, 12, 0, 2) }

        val etName = EditText(ctx).apply { hint = "Nombre del espacio"; setText(space?.name ?: "") }
        val etLocation = EditText(ctx).apply { hint = "Ubicación"; setText(space?.location ?: "") }
        val etCapacity = EditText(ctx).apply { hint = "Capacidad"; inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(space?.capacity?.toString() ?: "") }
        val etDescription = EditText(ctx).apply { hint = "Descripción"; minLines = 2; setText(space?.description ?: "") }

        val catLabels = arrayOf("Sala", "Laboratorio", "Auditorio", "Oficina")
        val catValues = arrayOf("SALA", "LABORATORIO", "AUDITORIO", "OFICINA")
        val spinnerCat = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, catLabels)
            val idx = catValues.indexOf(space?.category?.uppercase() ?: "SALA").coerceAtLeast(0)
            setSelection(idx)
        }

        val availLabels = arrayOf("Disponible", "Ocupado", "Mantenimiento")
        val availValues = arrayOf("DISPONIBLE", "OCUPADO", "MANTENIMIENTO")
        val spinnerAvail = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, availLabels)
            val idx = availValues.indexOf(space?.availability ?: "DISPONIBLE").coerceAtLeast(0)
            setSelection(idx)
        }

        val estadoLabels = arrayOf("Activo", "Inactivo")
        val spinnerEstado = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, estadoLabels)
            setSelection(if (space?.active != false) 0 else 1)
        }

        val cbStudents = CheckBox(ctx).apply { text = "Permitir para alumnos"; isChecked = space?.allowStudents ?: true }

        layout.addView(label("Nombre *")); layout.addView(etName)
        layout.addView(label("Categoría")); layout.addView(spinnerCat)
        layout.addView(label("Ubicación")); layout.addView(etLocation)
        layout.addView(label("Capacidad")); layout.addView(etCapacity)
        layout.addView(label("Descripción")); layout.addView(etDescription)
        layout.addView(cbStudents)
        layout.addView(label("Disponibilidad")); layout.addView(spinnerAvail)
        layout.addView(label("Estado")); layout.addView(spinnerEstado)

        val sv = ScrollView(ctx).apply { addView(layout) }

        val builder: () -> CreateSpaceRequest? = {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) { Toast.makeText(ctx, "El nombre es requerido", Toast.LENGTH_SHORT).show(); null }
            else CreateSpaceRequest(
                name = name,
                category = catValues[spinnerCat.selectedItemPosition],
                location = etLocation.text.toString().trim(),
                capacity = etCapacity.text.toString().toIntOrNull() ?: 0,
                description = etDescription.text.toString().trim(),
                allowStudents = cbStudents.isChecked,
                availability = availValues[spinnerAvail.selectedItemPosition],
                active = spinnerEstado.selectedItemPosition == 0
            )
        }
        return Pair(sv, builder)
    }

    private fun showCreateSpaceDialog() {
        val (view, buildRequest) = buildSpaceForm()
        AlertDialog.Builder(requireContext())
            .setTitle("Crear espacio")
            .setView(view)
            .setPositiveButton("Crear") { _, _ ->
                val req = buildRequest() ?: return@setPositiveButton
                lifecycleScope.launch {
                    try {
                        val resp = RetrofitClient.create(requireContext()).createSpace(req)
                        if (resp.isSuccessful) {
                            Toast.makeText(requireContext(), "Espacio creado", Toast.LENGTH_SHORT).show(); load()
                        } else Toast.makeText(requireContext(), "Error: ${resp.code()}", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) { Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show() }
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun showEditSpaceDialog(space: Space) {
        val ctx = requireContext()
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_space_edit, null)

        val etName        = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditSpaceName)
        val etLocation    = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditSpaceLocation)
        val etCapacity    = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditSpaceCapacity)
        val etDescription = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditSpaceDescription)
        val actvCategory  = view.findViewById<AutoCompleteTextView>(R.id.actvEditSpaceCategory)
        val actvAvail     = view.findViewById<AutoCompleteTextView>(R.id.actvEditSpaceAvailability)
        val actvStatus    = view.findViewById<AutoCompleteTextView>(R.id.actvEditSpaceStatus)
        val cbStudents    = view.findViewById<android.widget.CheckBox>(R.id.cbEditSpaceStudents)

        // Categoría
        val catLabels = arrayOf("Sala", "Laboratorio", "Auditorio", "Aula")
        val catValues = arrayOf("SALA", "LABORATORIO", "AUDITORIO", "AULA")
        actvCategory.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, catLabels))
        val catIdx = catValues.indexOfFirst { it.equals(space.category, ignoreCase = true) }.coerceAtLeast(0)
        actvCategory.setText(catLabels[catIdx], false)

        // Disponibilidad
        val availLabels = arrayOf("Disponible", "Ocupado", "Mantenimiento")
        val availValues = arrayOf("DISPONIBLE", "OCUPADO", "MANTENIMIENTO")
        actvAvail.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, availLabels))
        val availIdx = availValues.indexOfFirst { it == space.availability }.coerceAtLeast(0)
        actvAvail.setText(availLabels[availIdx], false)

        // Estado
        val statusLabels = arrayOf("Activo", "Inactivo")
        val statusValues = arrayOf(true, false)
        actvStatus.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, statusLabels))
        actvStatus.setText(if (space.active != false) "Activo" else "Inactivo", false)

        // Pre-llenar campos
        etName.setText(space.name)
        etLocation.setText(space.location)
        etCapacity.setText(space.capacity.toString())
        etDescription.setText(space.description)
        cbStudents.isChecked = space.allowStudents ?: true

        val dialog = AlertDialog.Builder(ctx)
            .setView(view)
            .create()
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        view.findViewById<TextView>(R.id.btnEditSpaceClose).setOnClickListener  { dialog.dismiss() }
        view.findViewById<TextView>(R.id.btnEditSpaceCancel).setOnClickListener { dialog.dismiss() }

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnEditSpaceSave)
            .setOnClickListener {
                val name = etName.text.toString().trim()
                val location = etLocation.text.toString().trim()
                val desc = etDescription.text.toString().trim()
                val capacity = etCapacity.text.toString().toIntOrNull()

                if (name.length < 3) {
                    Toast.makeText(ctx, "El nombre debe tener al menos 3 caracteres", Toast.LENGTH_SHORT).show(); return@setOnClickListener
                }
                if (capacity == null || capacity <= 0) {
                    Toast.makeText(ctx, "Ingresa una capacidad válida", Toast.LENGTH_SHORT).show(); return@setOnClickListener
                }

                val selectedCat = catValues[catLabels.indexOfFirst { it == actvCategory.text.toString() }.coerceAtLeast(0)]
                val selectedAvail = availValues[availLabels.indexOfFirst { it == actvAvail.text.toString() }.coerceAtLeast(0)]
                val isActive = actvStatus.text.toString() == "Activo"

                showConfirmDialog(ctx, "¿Guardar cambios?", "Se actualizarán los datos del espacio. ¿Deseas continuar?") {
                        lifecycleScope.launch {
                            try {
                                val req = CreateSpaceRequest(
                                    name          = name,
                                    category      = selectedCat,
                                    location      = location,
                                    capacity      = capacity,
                                    description   = desc,
                                    allowStudents = cbStudents.isChecked,
                                    availability  = selectedAvail,
                                    active        = isActive
                                )
                                val resp = RetrofitClient.create(ctx).updateSpace(space.id, req)
                                if (resp.isSuccessful) {
                                    Toast.makeText(ctx, "Espacio actualizado", Toast.LENGTH_SHORT).show()
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

    private fun showViewSpaceDialog(space: Space) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_space_detail, null)
        view.findViewById<TextView>(R.id.tvDetailName).text = space.name
        view.findViewById<TextView>(R.id.tvDetailCategory).text = space.category.ifBlank { "—" }
        view.findViewById<TextView>(R.id.tvDetailLocation).text = space.location.ifBlank { "—" }
        view.findViewById<TextView>(R.id.tvDetailCapacity).text = "${space.capacity} personas"
        view.findViewById<TextView>(R.id.tvDetailDescription).text = space.description.ifBlank { "—" }
        view.findViewById<TextView>(R.id.tvDetailStudents).text = if (space.allowStudents == true) "Permitido" else "Restringido"
        view.findViewById<TextView>(R.id.tvDetailStatus).text = if (space.active == true) "Activo" else "Inactivo"
        view.findViewById<TextView>(R.id.tvDetailAvailability).text = when (space.availability) {
            "DISPONIBLE" -> "Disponible"
            "OCUPADO" -> "Ocupado"
            "MANTENIMIENTO" -> "Mantenimiento"
            else -> space.availability ?: "—"
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Detalle de Espacio")
            .setView(view)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun showSpaceHistory(space: Space) {
        val ctx = requireContext()
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_space_history, null)
        view.findViewById<TextView>(R.id.tvHistorySpaceName).text = space.name
        val layoutEmpty = view.findViewById<View>(R.id.layoutEmpty)
        val rvHistory   = view.findViewById<RecyclerView>(R.id.rvHistory)
        layoutEmpty.visibility = View.VISIBLE
        rvHistory.visibility   = View.GONE

        val dialog = AlertDialog.Builder(ctx)
            .setView(view)
            .create()

        view.findViewById<TextView>(R.id.btnSpaceHistoryClose).setOnClickListener { dialog.dismiss() }
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSpaceHistoryDismiss)
            .setOnClickListener { dialog.dismiss() }

        dialog.show()
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        lifecycleScope.launch {
            try {
                val resp = RetrofitClient.create(ctx).getSpaceHistory(space.id)
                if (resp.isSuccessful) {
                    val items = resp.body() ?: emptyList()
                    if (items.isEmpty()) {
                        layoutEmpty.visibility = View.VISIBLE
                        rvHistory.visibility   = View.GONE
                    } else {
                        layoutEmpty.visibility = View.GONE
                        rvHistory.visibility   = View.VISIBLE
                        rvHistory.layoutManager = LinearLayoutManager(ctx)
                        rvHistory.adapter = SpaceHistoryAdapter(items)
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

class SpaceAdapter(
    private val items: List<Space>,
    private val onView: (Space) -> Unit,
    private val onEdit: (Space) -> Unit,
    private val onHistory: (Space) -> Unit,
    private val onToggle: (Space) -> Unit
) : RecyclerView.Adapter<SpaceAdapter.VH>() {

    inner class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_space, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        holder.view.apply {
            findViewById<TextView>(R.id.tvSpaceName).text = s.name
            findViewById<TextView>(R.id.tvCategory).text = s.category.ifBlank { "—" }
            findViewById<TextView>(R.id.tvLocation).text = s.location.ifBlank { "—" }
            findViewById<TextView>(R.id.tvCapacity).text = s.capacity.toString()

            // Badge ESTADO
            val badgeEstado = findViewById<TextView>(R.id.tvBadgeEstado)
            if (s.active == true) {
                badgeEstado.text = "Activo"
                badgeEstado.setBackgroundResource(R.drawable.bg_badge_green)
                badgeEstado.setTextColor(0xFF065F46.toInt())
            } else {
                badgeEstado.text = "Inactivo"
                badgeEstado.setBackgroundResource(R.drawable.bg_badge_red)
                badgeEstado.setTextColor(0xFF991B1B.toInt())
            }

            // Badge DISPONIBILIDAD
            val badgeDisp = findViewById<TextView>(R.id.tvBadgeDisp)
            when (s.availability ?: "") {
                "DISPONIBLE" -> {
                    badgeDisp.text = "Disponible"
                    badgeDisp.setBackgroundResource(R.drawable.bg_badge_green)
                    badgeDisp.setTextColor(0xFF065F46.toInt())
                }
                "OCUPADO" -> {
                    badgeDisp.text = "Ocupado"
                    badgeDisp.setBackgroundResource(R.drawable.bg_badge_red)
                    badgeDisp.setTextColor(0xFF991B1B.toInt())
                }
                else -> {
                    badgeDisp.text = "Mantenimiento"
                    badgeDisp.setBackgroundResource(R.drawable.bg_badge_yellow)
                    badgeDisp.setTextColor(0xFF92400E.toInt())
                }
            }

            findViewById<ImageButton>(R.id.btnView).setOnClickListener { onView(s) }
            findViewById<ImageButton>(R.id.btnEdit).setOnClickListener { onEdit(s) }
            findViewById<ImageButton>(R.id.btnHistory).setOnClickListener { onHistory(s) }
            findViewById<ImageButton>(R.id.btnToggle).setOnClickListener { onToggle(s) }
        }
    }
}

class SpaceHistoryAdapter(
    private val items: List<Reservation>
) : RecyclerView.Adapter<SpaceHistoryAdapter.VH>() {

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
