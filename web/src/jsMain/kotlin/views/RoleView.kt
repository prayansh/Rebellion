package views

import androidx.compose.runtime.Composable
import com.prayansh.coup.model.Influence
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
fun RoleView(it: Influence) {
    Div(attrs = {
        style {
            width(50.percent); paddingBottom(50.percent);
            marginRight(2.px); backgroundColor(Color.black)
            border(2.px)
            backgroundColor(it.role.color.css)
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Row)
        }
    }) {
        Span({
            style {
                padding(15.px)
                fontSize(24.px)
                letterSpacing(0.3.em)
                // TODO maybe add a border
                property("writing-mode", "vertical-lr")
            }
        }) {
            Text(it.role.name)
            if (!it.alive) {
                Text(" (DEAD)")
            }
        }
        Div(
            attrs = {
                style {
                    property("border-left", "2px solid white")
                    height(100.percent)
                }
            }
        )
    }
}
