package io.jactl;

public class Triple<X,Y,Z> {
  public final X first;
  public final Y second;
  public final Z third;
  public Triple(X first, Y second, Z third) {
    this.first  = first;
    this.second = second;
    this.third  = third;
  }
  public static <X,Y,Z> Triple<X,Y,Z> create(X first, Y second, Z third) {
    return new Triple(first, second, third);
  }
}
