#!/bin/perl
#
# Copyright 2022 James Crawford
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

@ARGV != 1 and die "Usage: perl GenerateClasses.pl file";

my @types;
my @fields;
my @attributes;
my $rootClass;
my $className;
my $extends;
my $inClass = 0;

open(FH, $ARGV[0]) or die "Couldn't open $ARGV[0]: $!\n";
while (<FH>) {
  # Strip '#' comments
  /^#/ and next;
  s/ *#.*$//;

  /^package / and do {
    print;
    print "\n";
    print "////////////////////////////////////////////////////////////////////\n";
    print "// File was generated using GenerateClasses.pl in tools directory\n";
    print "// DO NOT EDIT THIS FILE\n";
    print "////////////////////////////////////////////////////////////////////\n";
    print "\n";
  } and next;

  /^class +([A-Z][a-z]*)/ and $rootClass = $1 and do {
    print "abstract $_";
    print "\n  abstract <T> T accept(Visitor<T> visitor);\n\n";
  } and next;

  /^}/ and do {
    print "\n  interface Visitor<T> {\n";
    print "    T visit$_($_ \l$rootClass);\n" for @types;
    print "  }\n";
    print;
  } and next;

  /^ +}/ and $inClass and do {
    my @constructorFields;
    my @superFields;
    if ($extends ne $rootClass) {
      if (!exists $baseClasses{$extends}) {
        my $forwardRefence = grep { /^ +(abstract)? *class *$extends/ } <FH>;
        die(($forwardRefence ? "Forward reference to" : "Unknown") . " base class $extends");
      }
      push @superFields, map { $_->[0] } @{$baseClasses{$extends}};
      push @constructorFields, @{$baseClasses{$extends}};
    }
    push @constructorFields, @fields;
    print "    $className(" . join(', ', map { "$_->[1] $_->[0]"} @constructorFields) . ") {\n";
    @superFields and print "      super(" . join(", ", @superFields) . ");\n";
    print "      this.$_->[0] = $_->[0];\n" for @fields;
    my @tokenFields = @{[ grep {$_->[1] eq 'Token'} @constructorFields ]};
    my $firstTokenArg = @tokenFields && @tokenFields[0]->[0];
    # If no super field has a type Token then assign location in subclass
    if ($firstTokenArg and !grep { $_ eq $firstTokenArg } @superFields) {
      print "      this.location = $firstTokenArg;\n";
    }
    print "    }\n";
    print "    \@Override <T> T accept(Visitor<T> visitor) { return visitor.visit$className(this); }\n";
    my @fieldsAndAttrs;
    push @fieldsAndAttrs, map { $_->[0] } @constructorFields;
    #push @fieldsAndAttrs, @attributes;
    my $fieldValues = join(" + \", \" + ", map { "\"$_=\" + $_" } @fieldsAndAttrs);
    $fieldValues .= ' + ' if $fieldValues;
    print "    \@Override public String toString() { return \"$className" . "[\" + " . $fieldValues . "\"]\"; }\n";
    # Remember fields in case another class extends us
    my @a = map { $_ } @fields;
    $baseClasses{$className} = \@a;
    $inClass = 0;
    @fields = ();
    @attributes = ();
  } and print and next;

  $inClass and /([A-Za-z][a-zA-Z<,>.]*) +([a-zA-Z0-9]+);/  and push @fields, [$2, $1];
  $inClass and /([A-Za-z][a-zA-Z<,>.]*) +\@([a-zA-Z0-9]+)/ and push @attributes, $2;
  s/@//;

  /class (.*) extends +(\w*)/ and ($inClass, $className, $extends) = (1, $1, $2)
    and push @types, $className
    and s/class/static class/;

  print;
}

close(FH);
