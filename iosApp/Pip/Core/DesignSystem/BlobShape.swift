import SwiftUI

/// Pip's signature organic "blob" silhouette, used behind the mascot face
/// throughout the app (splash, buddy avatars, hero illustrations).
///
/// Mirrors the moc's CSS `border-radius: 42% 58% 65% 35% / 45% 40% 60% 55%`:
/// four quarter-ellipse corners (top-left, top-right, bottom-right,
/// bottom-left) whose horizontal and vertical radii each sum to 100% of the
/// width/height, so the corners meet edge-to-edge with no straight segments
/// and stay tangent-continuous at every seam.
struct BlobShape: Shape {
    /// Horizontal radius fractions, in CSS border-radius order: TL, TR, BR, BL.
    var horizontal: (topLeft: CGFloat, topRight: CGFloat, bottomRight: CGFloat, bottomLeft: CGFloat) = (0.42, 0.58, 0.65, 0.35)
    /// Vertical radius fractions, in CSS border-radius order: TL, TR, BR, BL.
    var vertical: (topLeft: CGFloat, topRight: CGFloat, bottomRight: CGFloat, bottomLeft: CGFloat) = (0.45, 0.40, 0.60, 0.55)

    /// Cubic-bezier constant that best approximates a quarter circle/ellipse arc.
    private let kappa: CGFloat = 0.5522847498

    func path(in rect: CGRect) -> Path {
        let w = rect.width
        let h = rect.height
        let x = rect.minX
        let y = rect.minY

        let aTL = horizontal.topLeft * w, bTL = vertical.topLeft * h
        let aTR = horizontal.topRight * w, bTR = vertical.topRight * h
        let aBR = horizontal.bottomRight * w, bBR = vertical.bottomRight * h
        let aBL = horizontal.bottomLeft * w, bBL = vertical.bottomLeft * h

        let topMid = CGPoint(x: x + aTL, y: y)
        let rightMid = CGPoint(x: x + w, y: y + bTR)
        let bottomMid = CGPoint(x: x + w - aBR, y: y + h)
        let leftMid = CGPoint(x: x, y: y + h - bBL)

        var path = Path()
        path.move(to: topMid)

        // Top-right corner: top edge -> right edge.
        path.addCurve(
            to: rightMid,
            control1: CGPoint(x: topMid.x + aTR * kappa, y: topMid.y),
            control2: CGPoint(x: rightMid.x, y: rightMid.y - bTR * kappa)
        )
        // Bottom-right corner: right edge -> bottom edge.
        path.addCurve(
            to: bottomMid,
            control1: CGPoint(x: rightMid.x, y: rightMid.y + bBR * kappa),
            control2: CGPoint(x: bottomMid.x + aBR * kappa, y: bottomMid.y)
        )
        // Bottom-left corner: bottom edge -> left edge.
        path.addCurve(
            to: leftMid,
            control1: CGPoint(x: bottomMid.x - aBL * kappa, y: bottomMid.y),
            control2: CGPoint(x: leftMid.x, y: leftMid.y + bBL * kappa)
        )
        // Top-left corner: left edge -> top edge, closing the blob.
        path.addCurve(
            to: topMid,
            control1: CGPoint(x: leftMid.x, y: leftMid.y - bTL * kappa),
            control2: CGPoint(x: topMid.x - aTL * kappa, y: topMid.y)
        )

        path.closeSubpath()
        return path
    }
}

#Preview {
    BlobShape()
        .fill(Color.blue)
        .frame(width: 160, height: 160)
        .padding(40)
}
