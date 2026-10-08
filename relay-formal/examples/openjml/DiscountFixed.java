/**
 * The same method with the precondition the contract needed. Expected from {@code run-esc.sh}:
 * no warnings, i.e. the postcondition is proven for every input the precondition allows.
 *
 * <p>The bounds on {@code price} are what keep {@code price * percentage} inside {@code int};
 * without them OpenJML reports a possible arithmetic overflow, which is a real bug too.
 */
public class DiscountFixed {

    //@ requires 0 <= price && price <= 1_000_000;
    //@ requires 0 <= percentage && percentage <= 100;
    //@ ensures 0 <= \result && \result <= price;
    public static int calculateDiscount(int price, int percentage) {
        return price * percentage / 100;
    }
}
