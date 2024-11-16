import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension

@Composable
fun VerticalScreenLayoutWithConstraintLayout() {
    ConstraintLayout(
        modifier = Modifier.fillMaxSize()
    ) {
        // Define references for the components
        val (a, b, c, d) = createRefs()

        // Component A with fixed height
        BasicText(
            "A",
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(Color.Red)
                .constrainAs(a) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
        )

        // Component B with fixed height
        BasicText(
            "B",
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(Color.Green)
                .constrainAs(b) {
                    top.linkTo(a.bottom)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
        )

        // Component C with flexible height (takes remaining space)
        BasicText(
            "C",
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Blue)
                .constrainAs(c) {
                    top.linkTo(b.bottom)
                    bottom.linkTo(d.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    height = Dimension.fillToConstraints
                }
        )

        // Component D with fixed height
        BasicText(
            "D",
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(Color.Yellow)
                .constrainAs(d) {
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewVerticalScreenLayoutWithConstraintLayout() {
    VerticalScreenLayoutWithConstraintLayout()
}
