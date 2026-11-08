package app.trazo.ui

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
            Text("Tu perfil", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Para calcular las kcal de tus salidas. Todo se queda en el móvil.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            fun digits(s: String) = s.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5)
            OutlinedTextField(
                weight, { weight = digits(it) },
                label = { Text("Peso (kg)") }, singleLine = true, isError = w == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    height, { height = digits(it) },
                    label = { Text("Altura (cm)") }, singleLine = true, isError = !heightOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    age, { age = it.filter(Char::isDigit).take(3) },
                    label = { Text("Edad") }, singleLine = true, isError = !ageOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = sex == Calories.Sex.MALE, onClick = { sex = Calories.Sex.MALE }, label = { Text("Hombre") })
                FilterChip(selected = sex == Calories.Sex.FEMALE, onClick = { sex = Calories.Sex.FEMALE }, label = { Text("Mujer") })
                FilterChip(selected = sex == null, onClick = { sex = null }, label = { Text("Sin decir") })
            }
            Text(
                if (h != null && a != null) "Las kcal se calculan con tu metabolismo basal (peso, altura, edad y sexo)."
                else "Con el peso basta. Si pones altura y edad, las kcal se ajustan a tu metabolismo basal, como en los relojes deportivos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Tu forma física", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Ajusta el tiempo estimado de las rutas y lo duras que son para ti. Cuando tengas rutas " +
                    "realizadas, pesa más tu ritmo real.",
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
            ) { Text("Guardar") }
        }
    }
}

/** Lo que Trazo sabe ya de tu ritmo por tus rutas realizadas. */
@Composable
private fun PaceLine(fitness: Fitness?) {
    val activity = AppMode.current
    val p = app.trazo.gpx.ride.PersonalPace.of(activity, fitness, UserProfile.done)
    val pct = ((p.factor - 1) * 100).roundToInt()
    val what = if (activity == app.trazo.gpx.Activity.HIKE) "a pie" else "en bici"
    Text(
        when {
            p.rides == 0 -> "Aún no hay rutas realizadas $what: se usa lo que marques aquí."
            abs(pct) < 3 -> "Con ${p.rides} ${if (p.rides == 1) "ruta realizada" else "rutas realizadas"} $what, vas al ritmo de la estimación de serie."
            else -> "Con ${p.rides} ${if (p.rides == 1) "ruta realizada" else "rutas realizadas"} $what, tardas un ${abs(pct)} % " +
                (if (pct > 0) "más" else "menos") + " que la estimación de serie."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
