package app.trazo.ui

import app.trazo.gpx.i18n.L
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import app.trazo.gpx.ride.Fitness
import kotlin.math.abs
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.trazo.gpx.ride.Calories
import kotlin.math.roundToInt

/** Número escrito con coma o punto, o null si no es válido o está fuera de [range]. */
private fun parse(text: String, range: ClosedFloatingPointRange<Double>) =
    text.replace(',', '.').toDoubleOrNull()?.takeIf { it in range }

/**
 * Tu perfil: peso (lo único necesario) y, para afinar las kcal con tu
 * metabolismo basal, altura, edad y sexo. Y tu forma física, para los
 * tiempos y lo dura que es cada ruta para ti.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var weight by remember { mutableStateOf(UserProfile.weightKg.roundToInt().toString()) }
    var height by remember { mutableStateOf(UserProfile.heightCm?.roundToInt()?.toString().orEmpty()) }
    var age by remember { mutableStateOf(UserProfile.ageYears?.toString().orEmpty()) }
    var sex by remember { mutableStateOf(UserProfile.sex) }
    var fitness by remember { mutableStateOf(UserProfile.fitness) }
    val w = parse(weight, 30.0..200.0)
    val h = parse(height, 120.0..220.0)
    val a = age.toIntOrNull()?.takeIf { it in 10..100 }
    val heightOk = height.isBlank() || h != null
    val ageOk = age.isBlank() || a != null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(L("Tu perfil", "Your profile"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                L("Para calcular las kcal de tus salidas. Todo se queda en el móvil.", "To work out the kcal of your outings. It all stays on your phone."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            fun digits(s: String) = s.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5)
            OutlinedTextField(
                weight, { weight = digits(it) },
                label = { Text(L("Peso (kg)", "Weight (kg)")) }, singleLine = true, isError = w == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    height, { height = digits(it) },
                    label = { Text(L("Altura (cm)", "Height (cm)")) }, singleLine = true, isError = !heightOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    age, { age = it.filter(Char::isDigit).take(3) },
                    label = { Text(L("Edad", "Age")) }, singleLine = true, isError = !ageOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = sex == Calories.Sex.MALE, onClick = { sex = Calories.Sex.MALE }, label = { Text(L("Hombre", "Male")) })
                FilterChip(selected = sex == Calories.Sex.FEMALE, onClick = { sex = Calories.Sex.FEMALE }, label = { Text(L("Mujer", "Female")) })
                FilterChip(selected = sex == null, onClick = { sex = null }, label = { Text(L("Sin decir", "Rather not say")) })
            }
            Text(
                if (h != null && a != null) L("Las kcal se calculan con tu metabolismo basal (peso, altura, edad y sexo).", "Kcal use your basal metabolic rate (weight, height, age and sex).")
                else L("Con el peso basta. Si pones altura y edad, las kcal se ajustan a tu metabolismo basal, como en los relojes deportivos.", "Weight is enough. Add height and age and kcal follow your basal metabolic rate, like sports watches do."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(L("Tu forma física", "Your fitness"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                L("Ajusta el tiempo estimado de las rutas y lo duras que son para ti. Cuando tengas rutas ", "It sets the estimated time of routes and how hard they are for you. Once you have completed ") +
                    L("realizadas, pesa más tu ritmo real.", "routes, your real pace counts for more."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.selectableGroup()) {
                Fitness.entries.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = fitness == f, onClick = { fitness = f }, role = Role.RadioButton)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = fitness == f, onClick = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(f.label, style = MaterialTheme.typography.bodyLarge)
                            Text(f.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            PaceLine(fitness)
            Button(
                enabled = w != null && heightOk && ageOk,
                onClick = {
                    UserProfile.save(context, w!!, h, a, sex, fitness)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(L("Guardar", "Save")) }
        }
    }
}

/** Lo que Trazo sabe ya de tu ritmo por tus rutas realizadas. */
@Composable
private fun PaceLine(fitness: Fitness?) {
    val activity = AppMode.current
    val p = app.trazo.gpx.ride.PersonalPace.of(activity, fitness, UserProfile.done)
    val pct = ((p.factor - 1) * 100).roundToInt()
    val what = if (activity == app.trazo.gpx.Activity.HIKE) L("a pie", "on foot") else L("en bici", "by bike")
    Text(
        when {
            p.rides == 0 -> L(
                "Aún no hay rutas realizadas $what: se usa lo que marques aquí.",
                "No completed routes $what yet: what you choose here is used.",
            )
            else -> {
                val done = if (p.rides == 1) L("ruta realizada", "completed route") else L("rutas realizadas", "completed routes")
                when {
                    abs(pct) < 3 -> L(
                        "Con ${p.rides} $done $what, vas al ritmo de la estimación de serie.",
                        "With ${p.rides} $done $what, you match the standard estimate.",
                    )
                    else -> L(
                        "Con ${p.rides} $done $what, tardas un ${abs(pct)} % " + (if (pct > 0) "más" else "menos") + " que la estimación de serie.",
                        "With ${p.rides} $done $what, you take ${abs(pct)}% " + (if (pct > 0) "longer" else "less") + " than the standard estimate.",
                    )
                }
            }
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
