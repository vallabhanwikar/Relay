/**
 * The design doc's demo method, as an AI assistant might write it: it compiles and a happy-path
 * test passes, but nothing stops a percentage above 100.
 *
 * <p>Expected from {@code run-esc.sh}: a postcondition violation with a counterexample where
 * {@code percentage > 100}, e.g. price = 100, percentage = 150.
 */
public class Discount {

    //@ requires 0 <= price && price <= 1_000_000;
    //@ requires 0 <= percentage && percentage <= 1_000;
    //@ ensures \result <= price;
    public static int calculateDiscount(int price, int percentage) {
        return price * percentage / 100;
    }
}
