package com.sgr.app.ui.admin

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sgr.app.R
import com.sgr.app.databinding.FragmentUsersBinding
import com.sgr.app.model.CreateUserRequest
import com.sgr.app.model.UpdateUserRequest
import com.sgr.app.model.User
import com.sgr.app.network.RetrofitClient
import kotlinx.coroutines.launch

class UsersFragment : Fragment() {

    private var _binding: FragmentUsersBinding? = null
    private val binding get() = _binding!!

    private var currentPage = 0
    private var totalPages = 1

    private val rolesLabels = arrayOf("Todos", "Administrador", "Usuario solicitante")
    private val rolesValues = arrayOf("", "ADMIN", "STUDENT")

    private val tiposLabels = arrayOf("Todos", "Estudiante", "Personal")
    private val tiposValues = arrayOf("", "ESTUDIANTE", "STAFF")

    private val estadosLabels = arrayOf("Todos", "Activo", "Inactivo")
    private val estadosValues = arrayOf("", "ACTIVE", "INACTIVE")

    private var filterRol = ""
    private var filterTipo = ""
    private var filterEstado = ""
    private var isInitializing = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().title = ""
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        setupSpinner(binding.spinnerRol, rolesLabels) { pos ->
            if (!isInitializing) { filterRol = rolesValues[pos]; filterTipo = ""; filterEstado = ""; currentPage = 0; load() }
        }
        setupSpinner(binding.spinnerTipo, tiposLabels) { pos ->
            if (!isInitializing) { filterTipo = tiposValues[pos]; filterRol = ""; filterEstado = ""; currentPage = 0; load() }
        }
        setupSpinner(binding.spinnerEstado, estadosLabels) { pos ->
            if (!isInitializing) { filterEstado = estadosValues[pos]; filterRol = ""; filterTipo = ""; currentPage = 0; load() }
        }

        isInitializing = false

        binding.btnClearFilters.setOnClickListener {
            filterRol = ""; filterTipo = ""; filterEstado = ""
            currentPage = 0
            isInitializing = true
            binding.spinnerRol.setSelection(0)
            binding.spinnerTipo.setSelection(0)
            binding.spinnerEstado.setSelection(0)
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
        val filter = when {
            filterEstado.isNotEmpty() -> filterEstado
            filterRol.isNotEmpty() -> filterRol
            filterTipo.isNotEmpty() -> filterTipo
            else -> ""
        }
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())
                val response = api.getUsers(currentPage, 4, filter, "")
                if (response.isSuccessful) {
                    val page = response.body() ?: return@launch
                    totalPages = page.totalPages.coerceAtLeast(1)
                    binding.tvPage.text = "Pág ${currentPage + 1} / $totalPages"
                    binding.recyclerView.adapter = UserAdapter(
                        items = page.content,
                        onView = { showViewUserDialog(it) },
                        onEdit = { showEditUserDialog(it) },
                        onToggle = { user ->
                            lifecycleScope.launch {
                                try { RetrofitClient.create(requireContext()).toggleUserStatus(user.id); load() }
                                catch (_: Exception) {}
                            }
                        }
                    )
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Error al cargar usuarios", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showUserFormDialog(user: User? = null) {
        val isCreate = user == null
        val ctx = requireContext()
        val dialogView = layoutInflater.inflate(R.layout.dialog_user_form, null)

        // Título
        dialogView.findViewById<TextView>(R.id.tvFormTitle).text =
            if (isCreate) "Nuevo Usuario" else "Editar Usuario"

        // Campos
        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etName)
        val etLastName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etLastName)
        val etBirthDate = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etBirthDate)
        val etIdentifier = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etIdentifier)
        val etEmail = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEmail)
        val etPhone = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPhone)
        val etPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPassword)

        // Fecha de nacimiento — máscara AAAA/MM/DD con / insertadas automáticamente
        etBirthDate.addTextChangedListener(object : android.text.TextWatcher {
            private var isFormatting = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (isFormatting) return
                isFormatting = true
                val digits = s?.filter { it.isDigit() }?.take(8) ?: ""
                val formatted = buildString {
                    digits.forEachIndexed { i, c ->
                        if (i == 4 || i == 6) append('/')
                        append(c)
                    }
                }
                s?.replace(0, s.length, formatted)
                android.text.Selection.setSelection(s, formatted.length)
                isFormatting = false
            }
        })

        // Pre-llenar si es edición
        user?.let {
            etName.setText(it.name)
            etLastName.setText(it.lastName)
            etBirthDate.setText((it.birthDate ?: "").replace('-', '/'))
            etIdentifier.setText(it.identifier ?: "")
            etEmail.setText(it.email)
            etPhone.setText(it.phone ?: "")
        }

        // Dropdowns de Rol
        val roleValues = arrayOf("STUDENT", "ADMIN")
        val roleLabels = arrayOf("Solicitante", "Administrador")
        val actvRole = dialogView.findViewById<AutoCompleteTextView>(R.id.spinnerRole)
        val roleAdapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, roleLabels)
        actvRole.setAdapter(roleAdapter)
        val roleIdx = roleValues.indexOf(user?.role ?: "STUDENT").coerceAtLeast(0)
        actvRole.setText(roleLabels[roleIdx], false)

        // Dropdowns de Tipo
        val userTypeValues = arrayOf("Estudiante", "Personal Académico", "Administrativo")
        val userTypeLabels = arrayOf("Estudiante", "Personal Académico", "Administrativo")
        val actvUserType = dialogView.findViewById<AutoCompleteTextView>(R.id.spinnerUserType)
        val typeAdapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, userTypeLabels)
        actvUserType.setAdapter(typeAdapter)
        val typeIdx = userTypeValues.indexOf(user?.userType ?: "Estudiante").coerceAtLeast(0)
        actvUserType.setText(userTypeLabels[typeIdx], false)

        // Diálogo pantalla completa
        val dialog = AlertDialog.Builder(ctx, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
            .setView(dialogView)
            .create()
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )

        dialogView.findViewById<TextView>(R.id.btnFormClose).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFormCancel).setOnClickListener { dialog.dismiss() }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFormSave).setOnClickListener {
            val name = etName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            val identifier = etIdentifier.text.toString().trim()
            val birthDate = etBirthDate.text.toString().trim().replace('/', '-')
            val phone = etPhone.text.toString().trim()
            val selectedRole = roleValues[roleLabels.indexOf(actvRole.text.toString()).coerceAtLeast(0)]
            val selectedType = userTypeValues[userTypeLabels.indexOf(actvUserType.text.toString()).coerceAtLeast(0)]

            if (name.isEmpty() || lastName.isEmpty() || email.isEmpty()) {
                Toast.makeText(ctx, "Nombre, apellidos y correo son requeridos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (identifier.isEmpty()) {
                Toast.makeText(ctx, "La matrícula o número de empleado es obligatoria", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (birthDate.isEmpty()) {
                Toast.makeText(ctx, "La fecha de nacimiento es obligatoria", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (phone.isEmpty() || !phone.matches(Regex("^\\d{10}$"))) {
                Toast.makeText(ctx, "El teléfono debe tener exactamente 10 dígitos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (isCreate && password.isEmpty()) {
                Toast.makeText(ctx, "La contraseña es requerida", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(ctx)
                .setTitle(if (isCreate) "¿Registrar usuario?" else "¿Guardar cambios?")
                .setMessage(if (isCreate) "Se creará un nuevo usuario. ¿Deseas continuar?" else "Se actualizarán los datos del usuario. ¿Deseas continuar?")
                .setPositiveButton(if (isCreate) "Sí, registrar" else "Sí, guardar") { _, _ ->
                    lifecycleScope.launch {
                        try {
                            val api = RetrofitClient.create(ctx)
                            val resp = if (isCreate) {
                                val req = CreateUserRequest(
                                    name = name, lastName = lastName, email = email,
                                    identifier = identifier,
                                    password = password,
                                    role = selectedRole, active = true,
                                    userType = selectedType,
                                    birthDate = birthDate,
                                    phone = phone
                                )
                                api.createUser(req)
                            } else {
                                val req = UpdateUserRequest(
                                    name = name, lastName = lastName, email = email,
                                    identifier = identifier,
                                    password = password.ifEmpty { null },
                                    role = selectedRole, active = user!!.active ?: true,
                                    userType = selectedType,
                                    birthDate = birthDate,
                                    phone = phone
                                )
                                api.updateUser(user.id, req)
                            }
                            if (resp.isSuccessful) {
                                Toast.makeText(ctx, if (isCreate) "Usuario creado" else "Usuario actualizado", Toast.LENGTH_SHORT).show()
                                dialog.dismiss(); load()
                            } else Toast.makeText(ctx, "Error: ${resp.code()}", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) { Toast.makeText(ctx, "Error de conexión", Toast.LENGTH_SHORT).show() }
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        dialog.show()
    }

    private fun showCreateUserDialog() = showUserFormDialog(null)

    private fun showEditUserDialog(user: User) {
        // Cargar datos completos desde la API antes de mostrar el formulario
        // (el listado no incluye birthDate, viene null sin este paso)
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.create(requireContext()).getUser(user.id)
                val fullUser = if (response.isSuccessful) response.body() ?: user else user
                showUserFormDialog(fullUser)
            } catch (_: Exception) {
                showUserFormDialog(user)
            }
        }
    }

    private fun showViewUserDialog(userPreview: User) {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density

        val scroll = ScrollView(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        scroll.addView(root)

        fun cell(label: String, value: String): LinearLayout {
            return LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFFF8FAFC.toInt())
                    cornerRadius = 12f * dp
                }
                setPadding(24, 14, 24, 14)
                addView(TextView(ctx).apply {
                    text = label.uppercase(); textSize = 10f
                    setTextColor(0xFF6B7280.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD
                })
                addView(TextView(ctx).apply {
                    text = value; textSize = 14f
                    setTextColor(0xFF111827.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setPadding(0, 4, 0, 0)
                })
            }
        }

        fun buildRows(u: User) {
            root.removeAllViews()
            val rolLabel = when (u.role) {
                "ADMIN" -> "Administrador"
                "STAFF" -> "Personal"
                else -> "Solicitante"
            }
            val tipoLabel = when (u.userType) {
                "Estudiante" -> "Estudiante"
                "Personal Académico" -> "Personal Académico"
                "Administrativo" -> "Administrativo"
                "ESTUDIANTE" -> "Estudiante"
                "STAFF" -> "Personal"
                else -> u.userType ?: "—"
            }
            val activeLabel = if (u.active == true) "Activo" else "Inactivo"

            fun row2(l1: String, v1: String, l2: String, v2: String) {
                root.addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.bottomMargin = (8 * dp).toInt(); layoutParams = lp
                    val c1 = cell(l1, v1).apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
                    val gap = android.view.View(ctx).apply { layoutParams = LinearLayout.LayoutParams((8 * dp).toInt(), 1) }
                    val c2 = cell(l2, v2).apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
                    addView(c1); addView(gap); addView(c2)
                })
            }

            fun row1(l: String, v: String) {
                root.addView(cell(l, v).apply {
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.bottomMargin = (8 * dp).toInt(); layoutParams = lp
                })
            }

            row2("Nombre", u.name, "Apellidos", u.lastName)
            row2("Fecha de nacimiento", u.birthDate ?: "—", "Rol", rolLabel)
            row2("Tipo de usuario", tipoLabel, "Matrícula / ID", u.identifier ?: "—")
            row2("Correo", u.email, "Teléfono", u.phone ?: "—")
            row1("Estado", activeLabel)
        }

        // Mostrar datos del listado inmediatamente
        buildRows(userPreview)

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("Detalle de Usuario")
            .setView(scroll)
            .setPositiveButton("Cerrar", null)
            .show()

        // Cargar datos frescos desde la API
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.create(ctx).getUser(userPreview.id)
                if (response.isSuccessful) {
                    response.body()?.let { fresh ->
                        if (dialog.isShowing) buildRows(fresh)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class UserAdapter(
    private val items: List<User>,
    private val onView: (User) -> Unit,
    private val onEdit: (User) -> Unit,
    private val onToggle: (User) -> Unit
) : RecyclerView.Adapter<UserAdapter.VH>() {

    inner class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val u = items[position]
        holder.view.apply {
            // Avatar con inicial
            val initial = u.name.firstOrNull()?.uppercase() ?: "?"
            findViewById<TextView>(R.id.tvAvatar).text = initial

            // Nombre y matrícula
            findViewById<TextView>(R.id.tvName).text = "${u.name} ${u.lastName}"
            findViewById<TextView>(R.id.tvIdentifier).text = "Matrícula: ${u.identifier ?: "—"}"

            // Correo
            findViewById<TextView>(R.id.tvEmail).text = u.email

            // Estado badge
            val tvStatus = findViewById<TextView>(R.id.tvStatus)
            tvStatus.text = if (u.active == true) "Activo" else "Inactivo"
            tvStatus.setBackgroundColor(if (u.active == true) 0xFF10B981.toInt() else 0xFFEF4444.toInt())

            // Rol badge
            val isAdmin = u.role == "ADMIN"
            val tvRole = findViewById<TextView>(R.id.tvRole)
            tvRole.text = if (isAdmin) "Administrador" else "Usuario solicitante"
            tvRole.setBackgroundColor(if (isAdmin) 0xFF7C3AED.toInt() else 0xFF2563EB.toInt())

            // Botones
            findViewById<android.widget.ImageButton>(R.id.btnView).setOnClickListener { onView(u) }
            findViewById<android.widget.ImageButton>(R.id.btnEdit).setOnClickListener { onEdit(u) }
            findViewById<android.widget.ImageButton>(R.id.btnToggle).setOnClickListener { onToggle(u) }
        }
    }
}
