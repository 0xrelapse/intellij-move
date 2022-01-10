package org.move.lang.core.psi.ext

import org.move.lang.core.psi.MvLetStatement
import org.move.lang.core.types.ty.Ty

val MvLetStatement.declaredTy: Ty? get() = this.typeAnnotation?.type?.inferTypeTy()