package org.move.lang.core.psi.impl

import com.intellij.lang.ASTNode
import org.move.lang.core.psi.MoveTypeReferenceElement
import org.move.lang.core.resolve.ref.MoveReference
import org.move.lang.core.resolve.ref.MoveTypeReferenceImpl

abstract class MoveTypeReferenceElementImpl(node: ASTNode) : MoveReferenceElementImpl(node),
                                                             MoveTypeReferenceElement {
    override fun getReference(): MoveReference = MoveTypeReferenceImpl(this)
}