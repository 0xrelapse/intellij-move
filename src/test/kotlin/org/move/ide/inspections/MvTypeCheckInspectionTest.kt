package org.move.ide.inspections

import org.move.utils.tests.annotation.InspectionTestBase

class MvTypeCheckInspectionTest : InspectionTestBase(MvTypeCheckInspection::class) {
    fun `test incorrect type address passed where &signer is expected`() = checkErrors(
        """
module 0x1::M {
    fun send(account: &signer) {}
    
    fun main(addr: address) {
        send(<error descr="Incompatible type 'address', expected '&signer'">addr</error>);
    }
} 
    """
    )

    fun `test incorrect type u8 passed where &signer is expected`() = checkErrors(
        """
module 0x1::M {
    fun send(account: &signer) {}
    
    fun main(addr: u8) {
        send(<error descr="Incompatible type 'u8', expected '&signer'">addr</error>)
    }
} 
    """
    )

    fun `test no errors if same type`() = checkErrors(
        """
        module 0x1::M {
            fun send(account: &signer) {}
            
            fun main(acc: &signer) {
                send(acc)
            }
        }        
    """
    )

    fun `test mutable reference compatible with immutable reference`() = checkErrors(
        """
    module 0x1::M {
        struct Option<Element> {
            vec: vector<Element>
        }
        fun is_none<Element>(t: &Option<Element>): bool {
            true
        }
        fun main(opt: &mut Option<Element>) {
            is_none(opt);
        } 
    }    
    """
    )

    fun `test same struct but different generic types`() = checkErrors(
        """
module 0x1::M {
    struct Option<Element> {}
    fun is_none<Elem>(t: Option<u64>): bool {
        true
    }
    fun main() {
        let opt = Option<u8> {};
        is_none(<error descr="Incompatible type 'Option<u8>', expected 'Option<u64>'">opt</error>);
    } 
}    
    """
    )

    fun `test different generic types for references`() = checkErrors(
        """
module 0x1::M {
    struct Option<Element> {}
    fun is_none<Elem>(t: &Option<u64>): bool {
        true
    }
    fun main() {
        let opt = &Option<u8> {};
        is_none(<error descr="Incompatible type '&Option<u8>', expected '&Option<u64>'">opt</error>);
    } 
}
    """
    )

    fun `test immutable reference is not compatible with mutable reference`() = checkErrors(
        """
module 0x1::M {
    struct Option<Element> {
        vec: vector<Element>
    }
    fun is_none<Element>(t: &mut Option<Element>): bool {
        true
    }
    fun main<Element>(opt: &Option<Element>) {
        is_none(<error descr="Incompatible type '&Option<Element>', expected '&mut Option<?Element>'">opt</error>);
    } 
}    
    """
    )

    fun `test incorrect type of argument with struct literal`() = checkErrors(
        """
module 0x1::M {
    struct A {}
    struct B {}
    
    fun use_a(a: A) {}
    fun main() {
        use_a(<error descr="Incompatible type 'B', expected 'A'">B {}</error>)            
    }
}
    """
    )

    fun `test incorrect type of argument with call expression`() = checkErrors(
        """
module 0x1::M {
    struct A {}
    struct B {}
    
    fun use_a(a: A) {}
    fun get_b(): B { B {} }
    
    fun main() {
        use_a(<error descr="Incompatible type 'B', expected 'A'">get_b()</error>)            
    }
}
    """
    )

    fun `test incorrect type of argument with call expression from different module`() = checkErrors(
        """
module 0x1::Other {
    struct B {}
    public fun get_b(): B { B {} }
}
module 0x1::M {
    use 0x1::Other::get_b;
    
    struct A {}
    fun use_a(a: A) {}
    
    fun main() {
        use_a(<error descr="Incompatible type 'B', expected 'A'">get_b()</error>)            
    }
}
    """
    )

    fun `test bytearray is vector of u8`() = checkErrors(
        """
        module 0x1::M {
            fun send(a: vector<u8>) {}
            fun main() {
                let a = b"deadbeef";
                send(a)
            }
        }        
    """
    )

    fun `test no error for compatible generic with explicit parameter`() = checkErrors(
        """
    module 0x1::M {
        struct Diem<CoinType> has store { val: u64 }
        struct Balance<Token> has key {
            coin: Diem<Token>
        }
        
        fun value<CoinType: store>(coin: &Diem<CoinType>) {}
        
        fun main<Token: store>() {
            let balance: Balance<Token>;
            let coin = &balance.coin;
            value<Token>(coin)
        }
    }        
    """
    )

    fun `test no error for compatible generic with inferred parameter`() = checkErrors(
        """
    module 0x1::M {
        struct Diem<CoinType> has store { val: u64 }
        struct Balance<Token> has key {
            coin: Diem<Token>
        }
        
        fun value<CoinType: store>(coin: &Diem<CoinType>) {}
        
        fun main<Token: store>() {
            let balance: Balance<Token>;
            let coin = &balance.coin;
            value(coin)
        }
    }        
    """
    )

    fun `test no return type but returns u8`() = checkErrors(
        """
    module 0x1::M {
        fun call() {
            return <error descr="Incompatible type 'integer', expected '()'">1</error>;
        }
    }    
    """
    )

    fun `test no return type but returns u8 with expression`() = checkErrors(
        """
    module 0x1::M {
        fun call() {
            <error descr="Incompatible type 'integer', expected '()'">1</error>
        }
    }    
    """
    )

    fun `test if statement returns ()`() = checkErrors(
        """
    module 0x1::M {
        fun m() {
            if (true) {1} else {2};
        }
    }    
    """
    )

    fun `test block expr returns ()`() = checkErrors(
        """
    module 0x1::M {
        fun m() {
            {1};
        }
    }    
    """
    )

    fun `test error on code block if empty block and return type`() = checkErrors(
        """
    module 0x1::M {
        fun call(): u8 {<error descr="Incompatible type '()', expected 'u8'">}</error>
    }    
        """
    )

    fun `test vector push back`() = checkErrors(
        """
    module 0x1::M {
        native public fun push_back<Element>(v: &mut vector<Element>, e: Element);
        
        fun m<E: drop>(v: &mut vector<E>, x: E): u8 {
            <error descr="Incompatible type '()', expected 'u8'">push_back(v, x)</error>
        }
    }    
    """
    )

    fun `test if condition should be boolean`() = checkErrors(
        """
    module 0x1::M {
        fun m() {
            if (<error descr="Incompatible type 'integer', expected 'bool'">1</error>) 1;
        }
    }    
    """
    )

    fun `test incompatible types from branches`() = checkErrors(
        """
    module 0x1::M {
        fun m() {
            if (true) {1} else {<error descr="Incompatible type 'bool', expected 'integer'">true</error>};
        }
    }    
    """
    )

    fun `test no type error with explicit generic as move_to`() = checkErrors(
        """
    module 0x1::M {
        struct Option<Element> has copy, drop, store {
            element: Element
        }
        public fun some<Element>(e: Element): Option<Element> {
            Option { element: e }
        }
        struct Vault<VaultContent: store> has key {
            content: Option<VaultContent>
        }
        public fun new<Content: store>(owner: &signer,  content: Content) {
            move_to<Vault<Content>>(
                owner,
                Vault { content: some(content) }
            )
        }
    }    
    """
    )

    fun `test type check incompatible constraints`() = checkErrors(
        """
module 0x1::M {
    struct C {}
    struct D {}
    fun new<Content>(a: Content, b: Content): Content { a }
    fun m() {
        new(C {}, <error descr="Incompatible type 'D', expected 'C'">D {}</error>);
    }
}
    """
    )

    fun `test error if resolved type requires a reference`() = checkErrors(
        """
module 0x1::M {
    fun index_of<Element>(v: &vector<Element>, e: &Element): (bool, u64) {
        (false, 0)
    }
    fun m() {
        let ids: vector<u64>;
        index_of(&ids, <error descr="Incompatible type 'u64', expected '&u64'">1u64</error>);
    }
}    
    """
    )

    fun `test return generic tuple from nested callable`() = checkErrors(
        """
    module 0x1::M {
        struct MintCapability<phantom CoinType> has key, store {}
        struct BurnCapability<phantom CoinType> has key, store {}

        public fun register_native_currency<FCoinType>(): (MintCapability<FCoinType>, BurnCapability<FCoinType>) {
            register_currency<FCoinType>()
        }
        public fun register_currency<FCoinType>(): (MintCapability<FCoinType>, BurnCapability<FCoinType>) {
            return (MintCapability<FCoinType>{}, BurnCapability<FCoinType>{})
        }
    }    
    """
    )

    fun `test emit event requires mutable reference error`() = checkErrors(
        """
module 0x1::M {
    struct EventHandle<phantom T: drop + store> has store {
        counter: u64,
        guid: vector<u8>,
    }
    struct Account has key {
        handle: EventHandle<Event>
    }
    struct Event has store, drop {}
    fun emit_event<T: drop + store>(handler_ref: &mut EventHandle<T>, msg: T) {}
    fun m<Type: store + drop>() acquires Account {
        emit_event(<error descr="Incompatible type 'EventHandle<Event>', expected '&mut EventHandle<?T>'">borrow_global_mut<Account>(@0x1).handle</error>, Event {});
    }
    
}    
    """
    )

    fun `test fields of struct should have abilities of struct`() = checkErrors(
        """
    module 0x1::M {
        struct A {}
        
        struct B has copy {
            <error descr="The type 'A' does not have the ability 'copy' required by the declared ability 'copy' of the struct 'B'">a: A</error>
        }
    }    
    """
    )

    fun `test key struct requires store fields`() = checkErrors(
        """
    module 0x1::M {
        struct A {}
        
        struct B has key {
            <error descr="The type 'A' does not have the ability 'store' required by the declared ability 'key' of the struct 'B'">a: A</error>
        }
    }    
    """
    )

    fun `test store struct requires store fields`() = checkErrors(
        """
    module 0x1::M {
        struct A {}
        
        struct B has store {
            <error descr="The type 'A' does not have the ability 'store' required by the declared ability 'store' of the struct 'B'">a: A</error>
        }
    }    
    """
    )

    fun `test copy struct requires copy fields`() = checkErrors(
        """
    module 0x1::M {
        struct A {}
        
        struct B has copy {
            <error descr="The type 'A' does not have the ability 'copy' required by the declared ability 'copy' of the struct 'B'">a: A</error>
        }
    }    
    """
    )

    fun `test drop struct requires drop fields`() = checkErrors(
        """
    module 0x1::M {
        struct A {}
        
        struct B has drop {
            <error descr="The type 'A' does not have the ability 'drop' required by the declared ability 'drop' of the struct 'B'">a: A</error>
        }
    }    
    """
    )

    fun `test function invocation with explicitly provided generic type`() = checkErrors(
        """
    module 0x1::Event {
        struct Message has drop {}
        
        public fun emit_event<T: store + drop>() {}
        
        public fun main() {
            emit_event<<error descr="The type 'Message' does not have required ability 'store'">Message</error>>()
        }
    }    
    """
    )

    fun `test struct constructor with explicitly provided generic type`() = checkErrors(
        """
    module 0x1::Event {
        struct Message has drop {}
        
        struct Event<Message: store + drop> {}
        
        public fun main() {
            Event<<error descr="The type 'Message' does not have required ability 'store'">Message</error>> {};
        }
    }    
    """
    )

    fun `test type param`() = checkErrors(
        """
    module 0x1::Event {
        struct Message has drop {}
        
        public fun emit_event<T: store + drop>() {}
        
        public fun main<M: drop>() {
            emit_event<<error descr="The type 'M' does not have required ability 'store'">M</error>>()
        }
    }    
    """
    )


    fun `test no required ability 'key' for move_to argument`() = checkErrors(
        """
    module 0x1::M {
        struct Res {}
        fun main(s: &signer, r: Res) {
            move_to(s, <error descr="The type 'Res' does not have required ability 'key'">r</error>)
        }
    }    
    """
    )

    fun `test no error in move_to with resource`() = checkErrors(
        """
    module 0x1::M {
        struct Res has key {}
        fun main(s: &signer, r: Res) {
            move_to<Res>(s, r)
        }
    }    
    """
    )

    fun `test no required ability for struct for type param`() = checkErrors(
        """
    module 0x1::M {
        struct Res {}
        fun save<T: key>(r: T) {}
        fun main(r: Res) {
            save(<error descr="The type 'Res' does not have required ability 'key'">r</error>)
        }
    }    
    """
    )

    fun `test no error in type param if structure has required abilities`() = checkErrors(
        """
    module 0x1::M {
        struct Res has key {}
        fun save<T: key>(r: T) {}
        fun main(r: Res) {
            save(r)
        }
    }    
    """
    )

    fun `test no error in specs`() = checkErrors(
        """
    module 0x1::M {
        fun balance<Token: store>() {}
        spec schema PayFromEnsures<Token> {
            ensures balance<Token>();
        }
    }    
    """
    )

    fun `test pass primitive type to generic with required abilities`() = checkErrors(
        """
    module 0x1::M {
        fun balance<Token: key>(k: Token) {}
        fun m() {
            balance(<error descr="The type 'address' does not have required ability 'key'">@0x1</error>);
        }
    }    
    """
    )

    fun `test invalid type for field in struct literal`() = checkErrors(
        """
module 0x1::M {
    struct Deal { val: u8 }
    fun main() {
        Deal { val: <error descr="Incompatible type 'bool', expected 'u8'">false</error> };
    }
}    
"""
    )

    fun `test valid type for field`() = checkErrors(
        """
    module 0x1::M {
        struct Deal { val: u8 }
        fun main() {
            Deal { val: 10 };
            Deal { val: 10u8 };
        }
    }    
    """
    )

    fun `test no need for explicit type parameter if inferrable from context`() = checkErrors(
        """
    module 0x1::M {
        struct Option<Element> has copy, drop, store {}
        public fun none<Element>(): Option<Element> {
            Option {}
        }
        struct S { field: Option<address> }
        fun m(): S {
            S { field: none() }
        }
        
    }
    """
    )

    fun `test no need for vector empty() generic`() = checkErrors(
        """
    module 0x1::M {
        /// Create an empty vector.
        native public fun empty<Element>(): vector<Element>;
        struct CapState<phantom Feature> has key {
            delegates: vector<address>
        }
        fun m() {
            CapState { delegates: empty() };
        }
    }    
    """
    )

    fun `test type error in struct literal field shorthand`() = checkErrors(
        """
    module 0x1::M {
        struct S { a: u8 }
        fun m() {
            let a = true;
            S { <error descr="Incompatible type 'bool', expected 'u8'">a</error> };
        }
    }    
    """
    )

    fun `test do not crash type checking invalid number of type params or call params`() = checkErrors(
        """
    module 0x1::M {
        struct S<R: key> { val: R }
        fun call(a: u8) {}
        fun m() {
            let s = S<u8, u8>{};
            call(1, 2, 3);
        }
    }    
    """
    )

    fun `test explicit unit return`() = checkErrors(
        """
    module 0x1::M {
        fun m(): () {}
    }    
    """
    )

    fun `test if else with references no error if coerceable`() = checkErrors(
        """
    module 0x1::M {
        struct S {}
        fun m(s: &S, s_mut: &mut S) {
            (if (cond) s_mut else s);
        }
    }    
    """
    )

    fun `test incorrect type address passed where &signer is expected in spec`() = checkErrors(
        """
module 0x1::M {
    fun send(account: &signer) {}
    
    spec send {
        send(<error descr="Incompatible type 'address', expected '&signer'">@0x1</error>);
    }
}   
    """
    )

    fun `test signer compatibility in spec`() = checkErrors(
        """
    module 0x1::M {
        fun address_of(account: &signer): address { @0x1 }
        fun send(account: &signer) {}
        spec send {
            address_of(account);
        }
    }    
    """
    )

    fun `test vector_u8 is compatible with vector_num inside spec`() = checkErrors(
        """
    module 0x1::M {
        struct S { 
            val: vector<u8> 
        }       
        spec module {
            S { val: b"" };
        }
    }    
    """
    )

    fun `test ref equality for generics in call expr`() = checkErrors(
        """
    module 0x1::M {
        struct Token<TokenT> {}
        fun call<TokenT>(ref: &Token<TokenT>) {
            let token = Token<TokenT> {};    
            spec {
                call(token);
            }
        }
    }    
    """
    )

    fun `test invalid argument to plus expr`() = checkErrors(
        """
    module 0x1::M {
        fun add(a: bool, b: bool) {
            <error descr="Invalid argument to '+': expected integer type, but found 'bool'">a</error> 
            + <error descr="Invalid argument to '+': expected integer type, but found 'bool'">b</error>;
        }
    }    
    """
    )

    fun `test invalid argument to minus expr`() = checkErrors(
        """
    module 0x1::M {
        fun add(a: bool, b: bool) {
            <error descr="Invalid argument to '-': expected integer type, but found 'bool'">a</error> 
            - <error descr="Invalid argument to '-': expected integer type, but found 'bool'">b</error>;
        }
    }    
    """
    )

    fun `test invalid argument to plus expr for type parameter`() = checkErrors(
        """
    module 0x1::M {
        fun add<T>(a: T, b: T) {
            <error descr="Invalid argument to '+': expected integer type, but found 'T'">a</error> 
            + <error descr="Invalid argument to '+': expected integer type, but found 'T'">b</error>;
        }
    }    
    """
    )

    fun `test no error if return nested in if and while`() = checkErrors(
        """
    module 0x1::M {
        fun main(): u8 {
            let i = 0;
            while (true) {
                if (true) return i
            };
            i
        }
    }    
    """
    )

    fun `test no error empty return`() = checkErrors(
        """
    module 0x1::M {
        fun main() {
            if (true) return
            return 
        }
    }    
    """
    )

    fun `test no error return tuple from if else`() = checkErrors(
        """
    module 0x1::M {
        fun main(): (u8, u8) {
            if (true) {
                return (1, 1) 
            } else {
                return (2, 2)
            }
        }
    }    
    """
    )

    fun `test no error return tuple from nested if else`() = checkErrors(
        """
    module 0x1::M {
        fun main(): (u8, u8) {
            if (true) {
                if (true) {
                    return (1, 1) 
                } else {
                    return (2, 2)
                }
            } else {
                return (3, 3)
            }
        }
    }    
    """
    )

    fun `test error add to bool in assignment expr`() = checkErrors(
        """
    module 0x1::M {
        fun main() {
            let a = 1u64;
            let b = false;
            a = a + <error descr="Invalid argument to '+': expected integer type, but found 'bool'">b</error>;
        }
    }    
    """
    )

    fun `test error invalid assignment type`() = checkErrors(
        """
    module 0x1::M {
        fun main() {
            let a = 1u64;
            a = <error descr="Incompatible type 'bool', expected 'u64'">false</error>;
        }
    }    
    """
    )

    fun `test tuple unpacking with three elements when two is specified`() = checkErrors(
        """
    module 0x1::M {
        fun tuple(): (u8, u8, u8) { (1, 1, 1) }
        fun main() {
            let <error descr="Invalid unpacking. Expected tuple binding of length 3: (_, _, _)">(a, b)</error> = tuple();
        }
    }    
    """
    )

    fun `test tuple unpacking no nested errors`() = checkErrors(
        """
    module 0x1::M {
        struct S { val: u8 }
        fun tuple(): (u8, u8, u8) { (1, 1, 1) }
        fun main() {
            let <error descr="Invalid unpacking. Expected tuple binding of length 3: (_, _, _)">(S { val }, b)</error> = tuple();
        }
    }    
    """
    )

    fun `test tuple unpacking into struct when tuple pat is expected is specified`() = checkErrors(
        """
    module 0x1::M {
        struct S { val: u8 }
        fun tuple(): (u8, u8, u8) { (1, 1, 1) }
        fun main() {
            let <error descr="Invalid unpacking. Expected tuple binding of length 3: (_, _, _)">S { val }</error> = tuple();
        }
    }    
    """
    )

    fun `test unpacking struct into field`() = checkErrors(
        """
    module 0x1::M {
        struct S { val: u8 }
        fun s(): S { S { val: 10 } }
        fun main() {
            let s = s();
        }
    }    
    """
    )

    fun `test error unpacking struct into tuple`() = checkErrors(
        """
    module 0x1::M {
        struct S { val: u8 }
        fun s(): S { S { val: 10 } }
        fun main() {
            let <error descr="Invalid unpacking. Expected struct binding of type 0x1::M::S">(a, b)</error> = s();
        }
    }    
    """
    )

    fun `test error parameter type with return type inferred`() = checkErrors(
        """
    module 0x1::M {
        fun identity<T>(a: T): T { a }
        fun main() {
            let a: u8 = <error descr="Incompatible type 'u64', expected 'u8'">identity(1u64)</error>;
        }
    }        
    """
    )

    fun `test no error integer should ignore spec blocks`() = checkErrors(
        """
    module 0x1::main {
        spec fun spec_pow(y: u64, x: u64): u64 {
            if (x == 0) {
                1
            } else {
                y * spec_pow(y, x - 1)
            }
        }

        /// Returns 10^degree.
        public fun pow_10(degree: u8): u64 {
            let res = 1;
            let i = 0;
            while ({
                spec {
                    invariant res == spec_pow(10, i);
                    invariant 0 <= i && i <= degree;
                };
                i < degree
            }) {
                res = res * 10;
                i = i + 1;
            };
            res
        }
    }        
    """
    )

    fun `test no error unpacking a struct from move_from`() = checkByText(
        """
module 0x1::main {
    struct Container has key { val: u8 }
    fun main() {
        let Container { val } = move_from(source_addr);
    }
}        
    """
    )

    fun `test vector lit with explicit type and type error`() = checkByText("""
module 0x1::main {
    fun main() {
        vector<u8>[<error descr="Incompatible type 'u64', expected 'u8'">1u64</error>];
    }
}        
    """)

    fun `test vector lit with implicit type and type error`() = checkByText("""
module 0x1::main {
    fun main() {
        vector[1u8, <error descr="Incompatible type 'u64', expected 'u8'">1u64</error>];
    }
}        
    """)

    fun `test call expr with incomplete arguments and explicit type`() = checkByText("""
    module 0x1::main {
        fun call<T>(a: T, b: T): T {
            b        
        }    
        fun main() {
            call<u8>(<error descr="Incompatible type 'u64', expected 'u8'">1u64</error>);
        }    
    }        
    """)

    fun `test call expr with incomplete arguments and implicit type`() = checkByText("""
    module 0x1::main {
        fun call<T>(a: T, b: T, c: T): T {
            b        
        }    
        fun main() {
            call(1u8, <error descr="Incompatible type 'u64', expected 'u8'">1u64</error>);
        }    
    }        
    """)

    fun `test option none is compatible with any option`() = checkByText("""
module 0x1::option {
    struct Option<Element> has copy, drop, store {
        vec: vector<Element>
    }
    public fun none<Element>(): Option<Element> {
        Option { vec: vector::empty() }
    }
}        
module 0x1::main {
    use 0x1::option;
    struct IterableValue<K: copy + store + drop> has store {
        prev: option::Option<K>,
        next: option::Option<K>,
    }
    public fun new() {
        IterableValue { prev: option::none(), next: option::none() };
    }
}        
    """)

    fun `test deeply nested structure type is unknown due to memory issues`() = checkByText("""
module 0x1::main {
    struct Box<T> has copy, drop, store { x: T }
    struct Box3<T> has copy, drop, store { x: Box<Box<T>> }
    struct Box7<T> has copy, drop, store { x: Box3<Box3<T>> }
    struct Box15<T> has copy, drop, store { x: Box7<Box7<T>> }
    struct Box31<T> has copy, drop, store { x: Box15<Box15<T>> }
    struct Box63<T> has copy, drop, store { x: Box31<Box31<T>> }
    
    fun box3<T>(x: T): Box3<T> {
        Box3 { x: Box { x: Box { x } } }
    }

    fun box7<T>(x: T): Box7<T> {
        Box7 { x: box3(box3(x)) }
    }

    fun box15<T>(x: T): Box15<T> {
        Box15 { x: box7(box7(x)) }
    }

    fun box31<T>(x: T): Box31<T> {
        Box31 { x: box15(box15(x)) }
    }
    
    fun box63<T>(x: T): Box63<T> {
        Box63 { x: box31(box31(x)) }
    }

    fun main() {
        let a: Box63<u8>;
        a;
      //^ unknown  
    }
}
    """)

    fun `test no invalid unpacking error for unresolved name tuple`() = checkByText("""
module 0x1::main {
    fun main() {
        let (a, b) = call();
    }
}        
    """)

    fun `test no invalid unpacking error for unresolved name struct`() = checkByText("""
module 0x1::main {
    struct S { val: u8 }
    fun main() {
        let S { val } = call();
    }
}        
    """)

    fun `test loop never returns and not a type error`() = checkByText("""
module 0x1::main {
    fun main(): u64 {
        let a = 1;
        if (a == 1) return a;
        loop {}
    }
}        
    """)

    fun `test integer arguments support ordering`() = checkByText("""
module 0x1::main {
    fun main(a: u64, b: u8) {
        let c = 1;
        a < b;
        a > b;
        a >= b;
        a <= b;
        a < c;
        b < c;
    }
}        
    """)

    fun `test cannot order references`() = checkByText("""
module 0x1::main {
    fun main(a: &u64, b: &u64) {
        <error descr="Invalid argument to '<': expected integer type, but found '&u64'">a</error> 
        < <error descr="Invalid argument to '<': expected integer type, but found '&u64'">b</error>;
    }
}        
    """)

    fun `test cannot order bools`() = checkByText("""
module 0x1::main {
    fun main(a: bool, b: bool) {
        <error descr="Invalid argument to '<': expected integer type, but found 'bool'">a</error> 
        < <error descr="Invalid argument to '<': expected integer type, but found 'bool'">b</error>;
    }
}        
    """)

    fun `test cannot order type parameters`() = checkByText("""
module 0x1::main {
    fun main<T>(a: T, b: T) {
        <error descr="Invalid argument to '<': expected integer type, but found 'T'">a</error> 
        < <error descr="Invalid argument to '<': expected integer type, but found 'T'">b</error>;
    }
}        
    """)

    fun `test equality is supported for the same type objects`() = checkByText("""
module 0x1::main {
    struct S { val: u8 }
    fun main<T>(a: T, b: T) {
        1 == 1;
        1u8 == 1u8;
        1u64 == 1u64;
        false == false;
        S { val: 10 } == S { val: 20 };
        a == b;
    }
}        
    """)

    fun `test inequality is supported for the same type objects`() = checkByText("""
module 0x1::main {
    struct S { val: u8 }
    fun main<T>(a: T, b: T) {
        1 != 1;
        1u8 != 1u8;
        1u64 != 1u64;
        false != false;
        S { val: 10 } != S { val: 20 };
        a != b;
    }
}        
    """)

    fun `test any ordering of types is allowed in specs`() = checkByText("""
module 0x1::liq_stake {
    spec main {
        let a = 1;
        let b = @0x1 < false;
        let c = a < false;
    }
}
    """)

    fun `test cannot equal completely different types`() = checkByText("""
module 0x1::main {
    struct S { val: u64 }
    fun main() {
        <error descr="Incompatible arguments to '==': 'integer' and 'bool'">1 == false</error>;
        <error descr="Incompatible arguments to '==': 'S' and 'bool'">S { val: 10 } == false</error>;
    }
}        
    """)

    fun `test cannot equal different integer types`() = checkByText("""
module 0x1::main {
    fun main() {
        <error descr="Incompatible arguments to '==': 'u8' and 'u64'">1u8 == 1u64</error>;
    }
}        
    """)

    fun `test cannot inequal different integer types`() = checkByText("""
module 0x1::main {
    fun main() {
        <error descr="Incompatible arguments to '!=': 'u8' and 'u64'">1u8 != 1u64</error>;
    }
}        
    """)

    fun `test logic expressions allow booleans`() = checkByText("""
module 0x1::main {
    fun main() {
        true && true;
        false || false;
    }
}        
    """)

    fun `test logic expressions invalid argument type`() = checkByText("""
module 0x1::main {
    fun main() {
        <error descr="Incompatible type 'u8', expected 'bool'">1u8</error> 
        && <error descr="Incompatible type 'u64', expected 'bool'">1u64</error>;
    }
}        
    """)

    fun `test if else with different generic parameters`() = checkByText("""
module 0x1::main {
    struct G<X, Y> {}
    fun main<X, Y>() {
        if (true) {
            G<X, Y> {}
        } else {
            <error descr="Incompatible type 'G<Y, X>', expected 'G<X, Y>'">G<Y, X> {}</error>
        };
    }
}        
    """)

    fun `test type cannot contain itself`() = checkByText("""
module 0x1::main {
    struct S { val: <error descr="Circular reference of type 'S'">S</error> }
}        
    """)

    fun `test type cannot contain itself in vector`() = checkByText("""
module 0x1::main {
    struct S { val: vector<<error descr="Circular reference of type 'S'">S</error>> }
}        
    """)

    fun `test cannot sum up bool and u64`() = checkByText("""
module 0x1::main {
    fun main() {
        <error descr="Invalid argument to '+': expected integer type, but found 'bool'">false</error> + 1u64;
    }
}        
    """)

    fun `test cannot sum up u8 and u64`() = checkByText("""
module 0x1::main {
    fun main() {
        <error descr="Incompatible arguments to '+': 'u8' and 'u64'">1u8 + 1u64</error>;
    }
}        
    """)
}
