package views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.web.events.SyntheticMouseEvent
import org.jetbrains.compose.web.attributes.AttrsBuilder
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLSpanElement

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
fun ClickableButton(text: String, onClick: (SyntheticMouseEvent) -> Unit) {
    Button(
        attrs = {
            onClick(onClick)
            style {
                margin(5.px)
                padding(5.px)
            }
        }
    ) {
        Text(text)
    }
}

@Composable
fun MyCheckbox(label: String, checked: MutableState<Boolean>) {
    Div {
        CheckboxInput(checked.value) {
            onInput { event -> checked.value = event.value }
        }
        Text(label)
    }
}

@Composable
fun MyLabel(label: String, fontSize: Int, attrs: (AttrsBuilder<HTMLSpanElement>.() -> Unit)? = null) {
    Span(attrs = { style { fontSize(fontSize.px) }; attrs?.let { this.apply(attrs) } }) {
        Text(label)
    }
}

// TODO potentially add a style + function to concatenate styles
