package views

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div

const val CheatsheetHTML = """
    <style type="text/css">
.tg  {border-collapse:collapse;border-spacing:0;}
.tg td{border-color:black;border-style:solid;border-width:1px;font-family:Arial, sans-serif;font-size:14px;
  overflow:hidden;padding:10px 5px;word-break:normal;}
.tg th{border-color:black;border-style:solid;border-width:1px;font-family:Arial, sans-serif;font-size:14px;
  font-weight:normal;overflow:hidden;padding:10px 5px;word-break:normal;}
.tg .tg-7keb{background-color:#ee5a24;color:#ffffff;font-family:Verdana, Geneva, sans-serif !important;text-align:left;
  vertical-align:top}
.tg .tg-n7d2{font-family:Verdana, Geneva, sans-serif !important;text-align:left;vertical-align:top}
.tg .tg-1qof{background-color:#222222;color:#ffffff;font-family:Verdana, Geneva, sans-serif !important;text-align:left;
  vertical-align:top}
.tg .tg-ov4x{background-color:#9980fa;border-color:#000000;color:#ffffff;font-family:Verdana, Geneva, sans-serif !important;
  text-align:left;vertical-align:top}
.tg .tg-4gk2{background-color:#0652dd;color:#ffffff;font-family:Verdana, Geneva, sans-serif !important;text-align:left;
  vertical-align:top}
.tg .tg-0jnt{background-color:#009432;color:#ffffff;font-family:Verdana, Geneva, sans-serif !important;text-align:left;
  vertical-align:top}
.tg .tg-p77p{background-color:#833471;color:#ffffff;font-family:Verdana, Geneva, sans-serif !important;text-align:left;
  vertical-align:top}
</style>
<table class="tg">
<thead>
  <tr>
    <th class="tg-1qof">Influences</th>
    <th class="tg-1qof">Actions</th>
    <th class="tg-1qof">Effects</th>
    <th class="tg-1qof">CounterAction</th>
  </tr>
</thead>
<tbody>
  <tr>
    <td class="tg-n7d2">----</td>
    <td class="tg-n7d2">Income</td>
    <td class="tg-n7d2">Collect 1 Coin</td>
    <td class="tg-n7d2">----</td>
  </tr>
  <tr>
    <td class="tg-n7d2">----</td>
    <td class="tg-n7d2">Foreign Aid</td>
    <td class="tg-n7d2">Collect 2 Coins</td>
    <td class="tg-n7d2">----</td>
  </tr>
  <tr>
    <td class="tg-n7d2">----</td>
    <td class="tg-n7d2">Coup</td>
    <td class="tg-n7d2">Pay 7 Coin</td>
    <td class="tg-n7d2">----</td>
  </tr>
  <tr>
    <td class="tg-ov4x">Politician</td>
    <td class="tg-ov4x">Tax</td>
    <td class="tg-ov4x">Collect 3 Coins</td>
    <td class="tg-ov4x">Block Foreign Aid</td>
  </tr>
  <tr>
    <td class="tg-4gk2">Sniper</td>
    <td class="tg-4gk2">Assassinate</td>
    <td class="tg-4gk2">Pay 3 Coins</td>
    <td class="tg-4gk2">----</td>
  </tr>
  <tr>
    <td class="tg-0jnt">Diplomat</td>
    <td class="tg-0jnt">Exchange</td>
    <td class="tg-0jnt">Draw 2 influences <br>and put 2 back</td>
    <td class="tg-0jnt">Block Stealing</td>
  </tr>
  <tr>
    <td class="tg-p77p">General</td>
    <td class="tg-p77p">Steal</td>
    <td class="tg-p77p">Steal 2 coins from another player</td>
    <td class="tg-p77p">Block Stealing</td>
  </tr>
  <tr>
    <td class="tg-7keb">Bodyguard</td>
    <td class="tg-7keb">----</td>
    <td class="tg-7keb">----</td>
    <td class="tg-7keb">Block Assassination</td>
  </tr>
</tbody>
</table>
"""

@Composable
fun CheatSheet(setShowCheatSheet: (Boolean) -> Unit) {
    Div(
        attrs = {
            style {
                display(DisplayStyle.Block)
                position(Position.Fixed)
                top(0.px)
                left(0.px)
                right(0.px)
                bottom(0.px)
                width(100.percent)
                height(100.percent)
                backgroundColor(rgba(0, 0, 0, 0.9f))
                property("z-index", 10)
                cursor("pointer")
            }
        }
    ) {
        ClickableButton("Close") {
            setShowCheatSheet(false)
        }
        Div(attrs = {
            ref { htmlDivElement ->
                htmlDivElement.innerHTML = CheatsheetHTML
                // htmlDivElement is a reference to the HTMLDivElement
                onDispose {
                    // add clean up code here
                }
            }
        }) {
            // Content()
        }
    }
}

