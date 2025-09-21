import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';

@Component({
  selector: 'ab-datepicker',
  standalone: true,
  imports: [CommonModule, FormsModule, MatFormFieldModule, MatInputModule, MatDatepickerModule, MatNativeDateModule],
  templateUrl: './datepicker.html',
  styleUrl: './datepicker.css'
})
export class Datepicker {
  @Input() label: string = '';
  @Input() placeholder: string = '';
  @Input() disabled: boolean = false;
  @Input() required: boolean = false;
  @Input() value: Date | null = null;
  @Input() min?: Date;
  @Input() max?: Date;
  @Output() valueChange = new EventEmitter<Date | null>();
}
