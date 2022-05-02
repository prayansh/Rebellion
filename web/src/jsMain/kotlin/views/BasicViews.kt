package views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.web.events.SyntheticMouseEvent
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Composable
fun InputText(label: String, text: MutableState<String>) {
    Div(
        attrs = {
            style {
//                justifyContent(JustifyContent.Center)
//                display(DisplayStyle.Flex)
//                flexDirection(FlexDirection.Column)
            }
        }
    ) {
        Span {
            Text(label)
        }
        Input(
            type = InputType.Text, // All InputTypes supported
            attrs = {
                value(text.value)
                onInput { event -> text.value = event.value }
            }
        )
    }
}

@Composable
fun RowDiv() {

}

@Composable
fun ColumnDiv() {

}

@Composable
fun ClickableButton(text: String, onClick: (SyntheticMouseEvent)-> Unit) {
    Button(
        attrs = {
            onClick(onClick)
        }
    ) {
        Text(text)
    }
}
